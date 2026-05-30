package collectionlog.collections;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * Loads special collection definitions (items without unique Material IDs)
 */
public class SpecialCollectionLoader {
    private final Plugin plugin;
    private final Gson gson;
    private final Map<String, CollectionDefinition> specialCollections;

    public SpecialCollectionLoader(Plugin plugin) {
        this.plugin = plugin;
        this.gson = new Gson();
        this.specialCollections = new HashMap<>();
    }

    /**
     * Load all special collections
     */
    public void loadSpecialCollections() {
        for (SpecialCollectionType type : SpecialCollectionType.values()) {
            loadSpecialCollection(type);
        }

        plugin.getLogger().info("✅ Loaded " + specialCollections.size() + " special collections");
    }

    private void loadSpecialCollection(SpecialCollectionType type) {
        try {
            String fileName = type.getJsonFile() + ".json";
            InputStream stream = getSpecialCollectionStream(fileName);
            if (stream == null) {
                plugin.getLogger().warning("Special collection file not found: " + fileName);
                return;
            }

            List<String> items;
            try (InputStream inputStream = stream) {
                // Handle different JSON formats
                if (type == SpecialCollectionType.MOB_HEADS) {
                    // Special handling for mob_heads.json (has vanilla/custom structure)
                    items = loadMobHeads(inputStream);
                } else if (type == SpecialCollectionType.JB_FISHING_PLUS || type == SpecialCollectionType.TCG) {
                    // Custom model data items (has name, rarity, customModelData)
                    items = loadCustomModelDataItems(inputStream);
                } else {
                    // Simple string arrays
                    Type listType = new TypeToken<List<String>>() { }.getType();
                    try (InputStreamReader reader = new InputStreamReader(inputStream)) {
                        items = gson.fromJson(reader, listType);
                    }

                    if (type == SpecialCollectionType.ENCHANTMENTS) {
                        items = expandEnchantmentLevels(items);
                    }
                }
            }

            CollectionDefinition collection = new CollectionDefinition(
                    type.getJsonFile(),
                    type.getDisplayName(),
                    "Special",
                    items,
                    new CollectionReward(1000, new ArrayList<>(), new ArrayList<>(), new ArrayList<>()),
                    true
            );

            specialCollections.put(type.getJsonFile(), collection);

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load special collection: " + type.getJsonFile(), e);
        }
    }

    /**
     * Gets an InputStream for a special collection file.
     * Priority:
     * 1. External file in plugins/CollectionLogPlugin/special_collections/
     * 2. Embedded resource in JAR
     *
     * If external file doesn't exist, attempts to copy from embedded resource.
     *
     * @param fileName The JSON filename (e.g., "tcg.json")
     * @return InputStream for the file, or null if not found
     */
    private InputStream getSpecialCollectionStream(String fileName) {
        File externalDir = new File(plugin.getDataFolder(), "special_collections");
        File externalFile = new File(externalDir, fileName);

        if (externalFile.exists()) {
            try {
                plugin.getLogger().fine("Loading " + fileName + " from external file");
                return new FileInputStream(externalFile);
            } catch (FileNotFoundException e) {
                plugin.getLogger().warning("External file exists but cannot be read: " + fileName);
            }
        }

        String resourcePath = "special_collections/" + fileName;
        byte[] resourceData;
        try (InputStream resourceStream = plugin.getResource(resourcePath)) {
            if (resourceStream == null) {
                plugin.getLogger().warning("Resource not found in JAR: " + resourcePath);
                return null;
            }
            resourceData = resourceStream.readAllBytes();
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to read resource from JAR: " + resourcePath, e);
            return null;
        }

        if (!externalDir.exists() && !externalDir.mkdirs()) {
            plugin.getLogger().warning("Failed to create special_collections directory");
            return new ByteArrayInputStream(resourceData);
        }

        try {
            Files.write(externalFile.toPath(), resourceData);
            plugin.getLogger().info("Copied " + fileName + " from JAR to " + externalFile.getPath());
            return new ByteArrayInputStream(resourceData);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to copy " + fileName + " to external location", e);
            plugin.getLogger().fine("Using embedded resource for " + fileName);
            return new ByteArrayInputStream(resourceData);
        }
    }

    private List<String> loadMobHeads(InputStream stream) {
        try {
            Type type = new TypeToken<Map<String, Object>>() { }.getType();
            Map<String, Object> data;
            try (InputStreamReader reader = new InputStreamReader(stream)) {
                data = gson.fromJson(reader, type);
            }
            List<String> items = new ArrayList<>();

            // Add vanilla heads
            if (data.containsKey("vanilla")) {
                Type listType = new TypeToken<List<String>>() { }.getType();
                String vanillaJson = gson.toJson(data.get("vanilla"));
                List<String> vanilla = gson.fromJson(vanillaJson, listType);
                items.addAll(vanilla);
            }

            // Add custom heads. Supports:
            // - ENTITY_NAME -> HeadDB ID (stored as ENTITY_NAME:HeadDBID)
            // - MYTHIC:HeadDBID -> display name (stored as DisplayName:HeadDBID)
            if (data.containsKey("custom")) {
                Type mapType = new TypeToken<Map<String, String>>() { }.getType();
                String customJson = gson.toJson(data.get("custom"));
                Map<String, String> custom = gson.fromJson(customJson, mapType);
                for (Map.Entry<String, String> entry : custom.entrySet()) {
                    if (entry.getKey().startsWith("MYTHIC:")) {
                        String headId = entry.getKey().substring("MYTHIC:".length()).trim();
                        String displayName = entry.getValue() != null ? entry.getValue().trim() : "";
                        if (!displayName.isBlank() && !headId.isBlank()) {
                            items.add(displayName + ":" + headId);
                        } else if (!headId.isBlank()) {
                            items.add(entry.getKey());
                        }
                    } else {
                        // EntityType entries use key=entity name and value=HeadDB ID, so compose
                        // the standard "ENTITY_NAME:<headId>" signature here.
                        items.add(entry.getKey() + ":" + entry.getValue());
                    }
                }
            }

            return items;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to parse mob_heads.json", e);
            return new ArrayList<>();
        }
    }

    private List<String> loadCustomModelDataItems(InputStream stream) {
        try {
            Type listType = new TypeToken<List<Map<String, Object>>>() { }.getType();
            List<Map<String, Object>> data;
            try (InputStreamReader reader = new InputStreamReader(stream)) {
                data = gson.fromJson(reader, listType);
            }

            List<String> items = new ArrayList<>();
            for (Map<String, Object> entry : data) {
                // Signature: name:customModelData
                String name = (String) entry.get("name");
                Object cmdObj = entry.get("customModelData");
                int cmd = cmdObj instanceof Number ? ((Number) cmdObj).intValue() : 0;

                items.add(name + ":" + cmd);
            }

            return items;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to parse custom model data items", e);
            return new ArrayList<>();
        }
    }

    private List<String> expandEnchantmentLevels(List<String> enchantments) {
        if (enchantments == null || enchantments.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> expanded = new ArrayList<>();
        for (String enchantmentKey : enchantments) {
            if (enchantmentKey == null || enchantmentKey.isBlank()) {
                continue;
            }

            Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(enchantmentKey.toLowerCase(Locale.ROOT)));
            if (enchantment == null) {
                expanded.add(enchantmentKey);
                continue;
            }

            int maxLevel = Math.max(1, enchantment.getMaxLevel());
            for (int level = 1; level <= maxLevel; level++) {
                expanded.add(enchantmentKey + ":" + level);
            }
        }
        return expanded;
    }

    /**
     * Get all special collections
     */
    public Map<String, CollectionDefinition> getSpecialCollections() {
        return specialCollections;
    }

    /**
     * Get a specific special collection
     */
    public CollectionDefinition getSpecialCollection(String id) {
        return specialCollections.get(id);
    }

    /**
     * Reload all special collections from JSON files.
     */
    public void reload() {
        specialCollections.clear();
        loadSpecialCollections();
    }
}
