package collectionlog.crafting;

import collectionlog.CollectionLogPlugin;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * Loads and manages custom crafting recipes defined in custom_recipes.yml.
 * Recipes are locked behind collection completions and dynamically
 * registered/unregistered with Bukkit.
 */
public class CustomRecipeManager {

    private static final String KEY_PREFIX = "collectionlog_";

    private final CollectionLogPlugin plugin;
    private final Map<String, CustomRecipe> recipes = new LinkedHashMap<>();
    private final Map<NamespacedKey, String> keyToRecipeId = new HashMap<>();
    private final Set<NamespacedKey> registeredKeys = new HashSet<>();

    public CustomRecipeManager(CollectionLogPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads recipes from custom_recipes.yml, unregistering any previously
     * registered custom recipes first.
     */
    public void loadRecipes() {
        // Remove previously registered recipes
        for (NamespacedKey key : registeredKeys) {
            Bukkit.removeRecipe(key);
        }
        recipes.clear();
        keyToRecipeId.clear();
        registeredKeys.clear();

        File recipesFile = new File(plugin.getDataFolder(), "custom_recipes.yml");
        if (!recipesFile.exists()) {
            plugin.saveResource("custom_recipes.yml", false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(recipesFile);
        ConfigurationSection recipesSection = config.getConfigurationSection("recipes");
        if (recipesSection == null) {
            plugin.getLogger().info("No custom recipes found in custom_recipes.yml");
            return;
        }

        for (String recipeId : recipesSection.getKeys(false)) {
            try {
                CustomRecipe recipe = parseRecipe(recipeId, config.getConfigurationSection("recipes." + recipeId));
                if (recipe != null) {
                    recipes.put(recipeId, recipe);
                    registerBukkitRecipe(recipe);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load custom recipe: " + recipeId, e);
            }
        }

        plugin.getLogger().info("Loaded " + recipes.size() + " custom recipe(s)");
    }

    private CustomRecipe parseRecipe(String id, ConfigurationSection section) {
        if (section == null) return null;

        String collectionRequired = section.getString("collection_required");
        String typeStr = section.getString("type", "shaped");

        CustomRecipe.RecipeType type;
        try {
            type = CustomRecipe.RecipeType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid recipe type '" + typeStr + "' for recipe: " + id);
            return null;
        }

        ConfigurationSection resultSection = section.getConfigurationSection("result");
        if (resultSection == null) {
            plugin.getLogger().warning("Missing 'result' section for recipe: " + id);
            return null;
        }
        String resultMaterial = resultSection.getString("material");
        if (resultMaterial == null) {
            plugin.getLogger().warning("Missing result 'material' for recipe: " + id);
            return null;
        }
        int resultAmount = resultSection.getInt("amount", 1);

        List<CustomRecipe.ReturnItem> returnItems = new ArrayList<>();
        for (Map<?, ?> entry : section.getMapList("return_items")) {
            String matStr = (String) entry.get("material");
            int amt = entry.containsKey("amount") ? ((Number) entry.get("amount")).intValue() : 1;
            Material mat = Material.getMaterial(matStr != null ? matStr : "");
            if (mat == null) {
                plugin.getLogger().warning("Invalid return item material '" + matStr + "' in recipe: " + id);
                continue;
            }
            returnItems.add(new CustomRecipe.ReturnItem(mat, amt));
        }

        if (type == CustomRecipe.RecipeType.SHAPED) {
            List<String> shape = section.getStringList("shape");
            if (shape.isEmpty()) {
                plugin.getLogger().warning("Shaped recipe '" + id + "' has no shape defined");
                return null;
            }
            ConfigurationSection ingredientsSection = section.getConfigurationSection("ingredients");
            Map<Character, String> ingredients = new HashMap<>();
            if (ingredientsSection != null) {
                for (String key : ingredientsSection.getKeys(false)) {
                    if (key.length() == 1) {
                        ingredients.put(key.charAt(0), ingredientsSection.getString(key));
                    }
                }
            }
            return new CustomRecipe(id, collectionRequired, type, shape, ingredients,
                    null, resultMaterial, resultAmount, returnItems);
        } else {
            List<String> shapelessIngredients = section.getStringList("ingredients");
            if (shapelessIngredients.isEmpty()) {
                plugin.getLogger().warning("Shapeless recipe '" + id + "' has no ingredients defined");
                return null;
            }
            return new CustomRecipe(id, collectionRequired, type, null, null,
                    shapelessIngredients, resultMaterial, resultAmount, returnItems);
        }
    }

    private void registerBukkitRecipe(CustomRecipe recipe) {
        NamespacedKey key = getNamespacedKey(recipe.getId());

        Material resultMat = getBaseMaterial(recipe.getResultMaterial());
        if (resultMat == null) {
            plugin.getLogger().warning("Invalid result material '" + recipe.getResultMaterial()
                    + "' for recipe: " + recipe.getId());
            return;
        }

        ItemStack result = new ItemStack(resultMat, recipe.getResultAmount());

        if (recipe.getType() == CustomRecipe.RecipeType.SHAPED) {
            ShapedRecipe shapedRecipe = new ShapedRecipe(key, result);
            String[] shapeArray = recipe.getShape().toArray(new String[0]);
            shapedRecipe.shape(shapeArray);

            for (Map.Entry<Character, String> entry : recipe.getIngredients().entrySet()) {
                RecipeChoice choice = parseIngredientChoice(entry.getValue(), recipe.getId());
                if (choice == null) {
                    plugin.getLogger().warning("Invalid ingredient '" + entry.getValue()
                            + "' for character '" + entry.getKey() + "' in recipe: " + recipe.getId());
                    return;
                }
                shapedRecipe.setIngredient(entry.getKey(), choice);
            }

            Bukkit.addRecipe(shapedRecipe);
        } else {
            ShapelessRecipe shapelessRecipe = new ShapelessRecipe(key, result);

            for (String ingredient : recipe.getShapelessIngredients()) {
                String[] parts = ingredient.split(":", 2);
                String materialName = parts[0];
                int count = 1;
                if (parts.length > 1) {
                    try {
                        count = Integer.parseInt(parts[1]);
                    } catch (NumberFormatException e) {
                        plugin.getLogger().warning("Invalid ingredient count in '" + ingredient
                                + "' for recipe: " + recipe.getId());
                        return;
                    }
                }

                Material mat = Material.getMaterial(materialName);
                if (mat == null) {
                    plugin.getLogger().warning("Invalid ingredient material '" + materialName
                            + "' in recipe: " + recipe.getId());
                    return;
                }
                shapelessRecipe.addIngredient(count, mat);
            }

            Bukkit.addRecipe(shapelessRecipe);
        }

        keyToRecipeId.put(key, recipe.getId());
        registeredKeys.add(key);
        plugin.getLogger().info("Registered custom recipe: " + recipe.getId());
    }

    private RecipeChoice parseIngredientChoice(String ingredientStr, String recipeId) {
        if (ingredientStr == null) return null;

        if (ingredientStr.endsWith(":*")) {
            String baseType = ingredientStr.substring(0, ingredientStr.length() - 2);
            List<Material> materials = getWildcardMaterials(baseType, recipeId);
            if (materials.isEmpty()) {
                plugin.getLogger().warning("No materials found for wildcard '" + ingredientStr
                        + "' in recipe: " + recipeId);
                return null;
            }
            return new RecipeChoice.MaterialChoice(materials);
        }

        Material mat = Material.getMaterial(ingredientStr);
        if (mat == null) return null;
        return new RecipeChoice.MaterialChoice(mat);
    }

    private List<Material> getWildcardMaterials(String baseType, String recipeId) {
        List<Material> materials = new ArrayList<>();

        if ("CONCRETE_POWDER".equals(baseType)) {
            for (DyeColor color : DyeColor.values()) {
                Material mat = Material.getMaterial(color.name() + "_CONCRETE_POWDER");
                if (mat != null) {
                    materials.add(mat);
                }
            }
        } else {
            plugin.getLogger().warning("Unknown wildcard base type '" + baseType
                    + "' in recipe: " + recipeId);
        }

        return materials;
    }

    /**
     * Returns the base Bukkit Material for the given result material string.
     * For "CONCRETE:MATCH", returns WHITE_CONCRETE as a placeholder; the real
     * color is resolved dynamically in the crafting event listener.
     */
    private Material getBaseMaterial(String materialStr) {
        if (materialStr == null) return null;

        if (materialStr.contains(":MATCH")) {
            String baseName = materialStr.substring(0, materialStr.indexOf(":MATCH"));
            // Use WHITE as the placeholder color for registration
            return Material.getMaterial("WHITE_" + baseName);
        }

        return Material.getMaterial(materialStr);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Returns the NamespacedKey used to register a custom recipe.
     */
    public NamespacedKey getNamespacedKey(String recipeId) {
        return new NamespacedKey(plugin, KEY_PREFIX + recipeId);
    }

    /**
     * Returns true if the given key corresponds to one of our custom recipes.
     */
    public boolean isCustomRecipe(NamespacedKey key) {
        return keyToRecipeId.containsKey(key);
    }

    /**
     * Returns the CustomRecipe associated with the given NamespacedKey, or null.
     */
    public CustomRecipe getRecipeByKey(NamespacedKey key) {
        String id = keyToRecipeId.get(key);
        return id != null ? recipes.get(id) : null;
    }

    /**
     * Returns the CustomRecipe with the given ID, or null.
     */
    public CustomRecipe getRecipeById(String id) {
        return recipes.get(id);
    }

    /**
     * Returns all custom recipes that require the given collection.
     */
    public List<CustomRecipe> getRecipesByCollection(String collectionName) {
        if (collectionName == null) return Collections.emptyList();
        List<CustomRecipe> result = new ArrayList<>();
        for (CustomRecipe recipe : recipes.values()) {
            if (collectionName.equalsIgnoreCase(recipe.getCollectionRequired())) {
                result.add(recipe);
            }
        }
        return result;
    }

    /**
     * Discovers all custom recipes the player has unlocked based on their
     * completed collections. Should be called on the main thread.
     */
    public void discoverRecipesForPlayer(Player player) {
        for (CustomRecipe recipe : recipes.values()) {
            String collection = recipe.getCollectionRequired();
            boolean hasCollection = collection == null
                    || plugin.getMilestoneManager().hasCompletedMilestone(player.getUniqueId(), collection);
            if (hasCollection) {
                player.discoverRecipe(getNamespacedKey(recipe.getId()));
            }
        }
    }

    /**
     * Removes recipe book discovery for all custom recipes tied to the given
     * collection. Should be called on the main thread.
     */
    public void undiscoverRecipesForPlayer(Player player, String collectionName) {
        for (CustomRecipe recipe : getRecipesByCollection(collectionName)) {
            player.undiscoverRecipe(getNamespacedKey(recipe.getId()));
        }
    }

    /**
     * Returns an unmodifiable view of all loaded custom recipes.
     */
    public Map<String, CustomRecipe> getRecipes() {
        return Collections.unmodifiableMap(recipes);
    }
}
