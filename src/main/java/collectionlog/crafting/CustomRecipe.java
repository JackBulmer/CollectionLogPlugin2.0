package collectionlog.crafting;

import org.bukkit.Material;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Represents a custom crafting recipe loaded from custom_recipes.yml.
 */
public class CustomRecipe {

    public enum RecipeType {
        SHAPED,
        SHAPELESS
    }

    public static class ReturnItem {
        private final Material material;
        private final int amount;

        public ReturnItem(Material material, int amount) {
            this.material = material;
            this.amount = amount;
        }

        public Material getMaterial() {
            return material;
        }

        public int getAmount() {
            return amount;
        }
    }

    private final String id;
    private final String collectionRequired;
    private final RecipeType type;
    private final List<String> shape;
    private final Map<Character, String> ingredients;
    private final List<String> shapelessIngredients;
    private final String resultMaterial;
    private final int resultAmount;
    private final List<ReturnItem> returnItems;

    public CustomRecipe(String id, String collectionRequired, RecipeType type,
                        List<String> shape, Map<Character, String> ingredients,
                        List<String> shapelessIngredients,
                        String resultMaterial, int resultAmount,
                        List<ReturnItem> returnItems) {
        this.id = id;
        this.collectionRequired = collectionRequired;
        this.type = type;
        this.shape = shape != null ? Collections.unmodifiableList(shape) : Collections.emptyList();
        this.ingredients = ingredients != null ? Collections.unmodifiableMap(ingredients) : Collections.emptyMap();
        this.shapelessIngredients = shapelessIngredients != null
                ? Collections.unmodifiableList(shapelessIngredients) : Collections.emptyList();
        this.resultMaterial = resultMaterial;
        this.resultAmount = resultAmount;
        this.returnItems = returnItems != null ? Collections.unmodifiableList(returnItems) : Collections.emptyList();
    }

    public String getId() {
        return id;
    }

    public String getCollectionRequired() {
        return collectionRequired;
    }

    public RecipeType getType() {
        return type;
    }

    public List<String> getShape() {
        return shape;
    }

    public Map<Character, String> getIngredients() {
        return ingredients;
    }

    public List<String> getShapelessIngredients() {
        return shapelessIngredients;
    }

    public String getResultMaterial() {
        return resultMaterial;
    }

    public int getResultAmount() {
        return resultAmount;
    }

    public List<ReturnItem> getReturnItems() {
        return returnItems;
    }
}
