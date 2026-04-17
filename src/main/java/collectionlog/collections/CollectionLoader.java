package collectionlog.collections;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.plugin.Plugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Loads collection definitions from collections.json (V1 format)
 */
public class CollectionLoader {
    private final Plugin plugin;
    private final Gson gson;
    private final Map<String, CollectionDefinition> collections;

    public CollectionLoader(Plugin plugin) {
        this.plugin = plugin;
        this.gson = new Gson();
        this.collections = new HashMap<>();
    }

    /**
     * Load all collections from collections.json
     */
    public void loadCollections() {
        try {
            InputStream stream = plugin.getResource("collections.json");
            if (stream == null) {
                plugin.getLogger().severe("collections.json not found in plugin resources!");
                return;
            }

            JsonObject root = gson.fromJson(new InputStreamReader(stream), JsonObject.class);

            // Parse nested structure: { "wood": { "oak": [...], "spruce": [...] }, ... }
            for (Map.Entry<String, JsonElement> categoryEntry : root.entrySet()) {
                String categoryName = categoryEntry.getKey();
                JsonElement categoryElement = categoryEntry.getValue();

                if (categoryElement.isJsonObject()) {
                    // Category with sub-collections (e.g., "wood" → "oak", "spruce")
                    JsonObject subCollectionsObj = categoryElement.getAsJsonObject();

                    // Create main collection
                    List<String> allItems = new ArrayList<>();
                    List<SubCollectionDefinition> subCollections = new ArrayList<>();

                    for (Map.Entry<String, JsonElement> subEntry : subCollectionsObj.entrySet()) {
                        String subName = subEntry.getKey();
                        Type listType = new com.google.gson.reflect.TypeToken<List<String>>() { }.getType();
                        List<String> subItems = gson.fromJson(subEntry.getValue(), listType);

                        allItems.addAll(subItems);

                        // Create sub-collection
                        SubCollectionDefinition subCollection = new SubCollectionDefinition(
                                categoryName + "_" + subName,
                                capitalize(subName),
                                subItems,
                                new CollectionReward(100, new ArrayList<>(), new ArrayList<>(), new ArrayList<>())
                        );
                        subCollections.add(subCollection);
                    }

                    CollectionDefinition collection = new CollectionDefinition(
                            categoryName,
                            capitalize(categoryName),
                            "Standard", // Default category
                            allItems,
                            new CollectionReward(500, new ArrayList<>(), new ArrayList<>(), new ArrayList<>()),
                            false
                    );

                    for (SubCollectionDefinition sub : subCollections) {
                        collection.addSubCollection(sub);
                    }

                    collections.put(categoryName, collection);

                } else if (categoryElement.isJsonArray()) {
                    // Simple collection (e.g., "end": [...])
                    Type listType = new com.google.gson.reflect.TypeToken<List<String>>() { }.getType();
                    List<String> items = gson.fromJson(categoryElement, listType);

                    CollectionDefinition collection = new CollectionDefinition(
                            categoryName,
                            capitalize(categoryName),
                            "Standard",
                            items,
                            new CollectionReward(500, new ArrayList<>(), new ArrayList<>(), new ArrayList<>()),
                            false
                    );

                    collections.put(categoryName, collection);
                }
            }

            plugin.getLogger().info("✅ Loaded " + collections.size() + " standard collections");

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load collections.json", e);
        }
    }

    /**
     * Get all loaded collections
     */
    public Map<String, CollectionDefinition> getCollections() {
        return collections;
    }

    /**
     * Get a specific collection by ID
     */
    public CollectionDefinition getCollection(String id) {
        return collections.get(id);
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1).replace("_", " ");
    }
}
