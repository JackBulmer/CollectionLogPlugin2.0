package collectionlog.collections;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Loads insert definitions from insert_in_collections/*.json.
 */
public class InsertCollectionLoader {
    private static final String DIRECTORY_NAME = "insert_in_collections";
    private static final Map<String, Material> REGISTERED_MATERIALS = new LinkedHashMap<>();

    private final Plugin plugin;
    private final File externalDirectory;
    private final Map<String, InsertCollectionDefinition> insertDefinitions;

    public InsertCollectionLoader(Plugin plugin) {
        this.plugin = plugin;
        this.externalDirectory = new File(plugin.getDataFolder(), DIRECTORY_NAME);
        this.insertDefinitions = new LinkedHashMap<>();
    }

    public void loadInsertCollections() {
        insertDefinitions.clear();
        REGISTERED_MATERIALS.clear();
        ensureExternalDirectoryExists();

        File[] files = externalDirectory.listFiles((dir, name) -> name.endsWith(".json"));
        if (files != null && files.length > 0) {
            Arrays.sort(files);
            for (File file : files) {
                loadFromFile(file);
            }
            plugin.getLogger().info("✅ Loaded " + insertDefinitions.size() + " insert collection definitions");
            return;
        }

        loadFromResources();
        plugin.getLogger().info("✅ Loaded " + insertDefinitions.size() + " insert collection definitions");
    }

    private void ensureExternalDirectoryExists() {
        if (!externalDirectory.exists() && !externalDirectory.mkdirs()) {
            plugin.getLogger().warning("Failed to create insert_in_collections directory");
        }
    }

    private void loadFromFile(File file) {
        try (FileReader reader = new FileReader(file)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            InsertCollectionDefinition definition = parseInsertDefinition(json, file.getName());
            if (definition != null) {
                insertDefinitions.put(definition.getCollectionId(), definition);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load insert collection file: " + file.getName(), e);
        }
    }

    private void loadFromResources() {
        try (InputStream indexStream = plugin.getResource(DIRECTORY_NAME + "/index.txt")) {
            if (indexStream == null) {
                return;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(indexStream))) {
                String fileName;
                while ((fileName = reader.readLine()) != null) {
                    String trimmed = fileName.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.endsWith(".json")) {
                        continue;
                    }

                    loadEmbeddedFile(trimmed);
                }
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to read insert_in_collections/index.txt", e);
        }
    }

    private void loadEmbeddedFile(String fileName) {
        String resourcePath = DIRECTORY_NAME + "/" + fileName;
        File externalFile = new File(externalDirectory, fileName);
        if (externalFile.exists()) {
            try (InputStream dataStream = new FileInputStream(externalFile)) {
                JsonObject json = JsonParser.parseReader(new InputStreamReader(dataStream)).getAsJsonObject();
                InsertCollectionDefinition definition = parseInsertDefinition(json, fileName);
                if (definition != null) {
                    insertDefinitions.put(definition.getCollectionId(), definition);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to parse insert collection file: " + fileName, e);
            }
            return;
        }

        byte[] resourceData;
        try (InputStream resourceStream = plugin.getResource(resourcePath)) {
            if (resourceStream == null) {
                plugin.getLogger().warning("Insert collection resource not found: " + resourcePath);
                return;
            }
            resourceData = resourceStream.readAllBytes();
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to read insert collection resource: " + resourcePath, e);
            return;
        }

        try {
            Files.write(externalFile.toPath(), resourceData);
            plugin.getLogger().info("Copied " + fileName + " from JAR to " + externalFile.getPath());
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to copy insert collection file: " + fileName, e);
        }

        try (InputStream dataStream = externalFile.exists()
                ? new FileInputStream(externalFile)
                : new ByteArrayInputStream(resourceData)) {
            JsonObject json = JsonParser.parseReader(new InputStreamReader(dataStream)).getAsJsonObject();
            InsertCollectionDefinition definition = parseInsertDefinition(json, fileName);
            if (definition != null) {
                insertDefinitions.put(definition.getCollectionId(), definition);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to parse insert collection file: " + fileName, e);
        }
    }

    private InsertCollectionDefinition parseInsertDefinition(JsonObject json, String sourceName) {
        if (json == null || !json.has("collection_id") || !json.has("items")) {
            plugin.getLogger().warning("Invalid insert collection format in " + sourceName);
            return null;
        }

        String collectionId = json.get("collection_id").getAsString();
        JsonArray itemsArray = json.getAsJsonArray("items");
        List<InsertItemDefinition> items = new ArrayList<>();

        for (JsonElement element : itemsArray) {
            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject itemObject = element.getAsJsonObject();
            if (!itemObject.has("name") || !itemObject.has("customModelData")) {
                plugin.getLogger().warning("Skipping invalid insert item in " + sourceName + " (missing name/customModelData)");
                continue;
            }

            String name = itemObject.get("name").getAsString();
            int customModelData = itemObject.get("customModelData").getAsInt();
            String subCollection = itemObject.has("sub_collection")
                    ? itemObject.get("sub_collection").getAsString()
                    : null;
            Material material = parseMaterial(itemObject, name, sourceName);

            InsertItemDefinition itemDefinition = new InsertItemDefinition(name, customModelData, subCollection, material);
            items.add(itemDefinition);
            REGISTERED_MATERIALS.put(itemDefinition.getSignature(), itemDefinition.getMaterial());
        }

        return new InsertCollectionDefinition(collectionId, items);
    }

    private Material parseMaterial(JsonObject itemObject, String itemName, String sourceName) {
        if (!itemObject.has("material")) {
            return Material.PAPER;
        }

        String materialName = itemObject.get("material").getAsString();
        Material material = Material.matchMaterial(materialName);
        if (material != null) {
            return material;
        }

        plugin.getLogger().warning("Invalid material '" + materialName + "' for insert item '" + itemName
                + "' in " + sourceName + "; defaulting to PAPER");
        return Material.PAPER;
    }

    public static Material getRegisteredMaterial(String signature) {
        return REGISTERED_MATERIALS.get(signature);
    }

    public Map<String, InsertCollectionDefinition> getInsertDefinitions() {
        return Collections.unmodifiableMap(insertDefinitions);
    }

    public void reload() {
        loadInsertCollections();
    }
}
