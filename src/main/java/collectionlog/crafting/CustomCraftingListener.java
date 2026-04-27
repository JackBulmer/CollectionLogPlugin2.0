package collectionlog.crafting;

import collectionlog.CollectionLogPlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import java.util.List;
import java.util.Map;

/**
 * Handles crafting events for custom recipes defined in custom_recipes.yml.
 *
 * <ul>
 *   <li>Blocks crafting if the player hasn't completed the required collection.</li>
 *   <li>Performs dynamic color matching for the concrete recipe.</li>
 *   <li>Returns bonus items (e.g., a bucket) to the player after crafting.</li>
 * </ul>
 */
public class CustomCraftingListener implements Listener {

    private final CollectionLogPlugin plugin;
    private final CustomRecipeManager customRecipeManager;

    public CustomCraftingListener(CollectionLogPlugin plugin) {
        this.plugin = plugin;
        this.customRecipeManager = plugin.getCustomRecipeManager();
    }

    /**
     * Validates the collection requirement and resolves color-matched results
     * before the crafting result is displayed.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareItemCraft(PrepareItemCraftEvent event) {
        Recipe recipe = event.getRecipe();
        if (recipe == null) return;

        NamespacedKey key = getRecipeKey(recipe);
        if (key == null || !customRecipeManager.isCustomRecipe(key)) return;

        HumanEntity human = event.getView().getPlayer();
        if (!(human instanceof Player player)) return;

        CustomRecipe customRecipe = customRecipeManager.getRecipeByKey(key);
        if (customRecipe == null) return;

        // Check collection requirement
        String collectionRequired = customRecipe.getCollectionRequired();
        if (collectionRequired != null
                && !plugin.getMilestoneManager().hasCompletedMilestone(
                        player.getUniqueId(), collectionRequired)) {
            event.getInventory().setResult(new ItemStack(Material.AIR));
            player.sendMessage("§cYou need to complete the §e"
                    + formatCollectionName(collectionRequired)
                    + " §ccollection to craft this!");
            return;
        }

        // Resolve color-matched result (e.g., CONCRETE:MATCH)
        String resultMaterial = customRecipe.getResultMaterial();
        if (resultMaterial != null && resultMaterial.contains(":MATCH")) {
            ItemStack matchedResult = resolveColorMatchedResult(customRecipe, event.getInventory().getMatrix());
            if (matchedResult != null) {
                event.getInventory().setResult(matchedResult);
            } else {
                event.getInventory().setResult(new ItemStack(Material.AIR));
            }
        }
    }

    /**
     * Enforces collection requirements at craft time and handles return items
     * (e.g., giving the player a bucket after using a water bucket).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        Recipe recipe = event.getRecipe();
        if (recipe == null) return;

        NamespacedKey key = getRecipeKey(recipe);
        if (key == null || !customRecipeManager.isCustomRecipe(key)) return;

        if (!(event.getWhoClicked() instanceof Player player)) return;

        CustomRecipe customRecipe = customRecipeManager.getRecipeByKey(key);
        if (customRecipe == null) return;

        // Safety check – block crafting if the player lost the collection since
        // the last PrepareItemCraftEvent update
        String collectionRequired = customRecipe.getCollectionRequired();
        if (collectionRequired != null
                && !plugin.getMilestoneManager().hasCompletedMilestone(
                        player.getUniqueId(), collectionRequired)) {
            event.setCancelled(true);
            return;
        }

        // Return items (e.g., bucket from water bucket)
        List<CustomRecipe.ReturnItem> returnItems = customRecipe.getReturnItems();
        if (!returnItems.isEmpty()) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                for (CustomRecipe.ReturnItem returnItem : returnItems) {
                    ItemStack item = new ItemStack(returnItem.getMaterial(), returnItem.getAmount());
                    Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
                    // Drop any items that didn't fit in the inventory
                    leftover.values().forEach(i ->
                            player.getWorld().dropItemNaturally(player.getLocation(), i));
                }
            });
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private NamespacedKey getRecipeKey(Recipe recipe) {
        if (recipe instanceof ShapedRecipe shaped) return shaped.getKey();
        if (recipe instanceof ShapelessRecipe shapeless) return shapeless.getKey();
        return null;
    }

    /**
     * Scans the crafting matrix for a concrete-powder ingredient and returns a
     * new ItemStack with the matching concrete color.
     */
    private ItemStack resolveColorMatchedResult(CustomRecipe customRecipe, ItemStack[] matrix) {
        String resultMaterial = customRecipe.getResultMaterial();
        String baseName = resultMaterial.substring(0, resultMaterial.indexOf(":MATCH"));

        for (ItemStack item : matrix) {
            if (item == null || item.getType() == Material.AIR) continue;
            String matName = item.getType().name();
            if (matName.endsWith("_CONCRETE_POWDER")) {
                String colorPrefix = matName.replace("_CONCRETE_POWDER", "");
                Material resultMat = Material.getMaterial(colorPrefix + "_" + baseName);
                if (resultMat != null) {
                    return new ItemStack(resultMat, customRecipe.getResultAmount());
                }
            }
        }

        return null;
    }

    private String formatCollectionName(String collectionName) {
        if (collectionName == null || collectionName.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String word : collectionName.replace("_", " ").split(" ")) {
            if (word.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) sb.append(word.substring(1).toLowerCase());
        }
        return sb.toString();
    }
}
