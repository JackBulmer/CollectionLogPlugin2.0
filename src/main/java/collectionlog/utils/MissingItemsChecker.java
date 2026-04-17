package collectionlog.utils;

import collectionlog.collections.CollectionDefinition;
import collectionlog.collections.CollectionLoader;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Dev utility to find missing items from collections.json
 */
public class MissingItemsChecker {
    private final Plugin plugin;
    private final CollectionLoader collectionLoader;

    public MissingItemsChecker(Plugin plugin, CollectionLoader collectionLoader) {
        this.plugin = plugin;
        this.collectionLoader = collectionLoader;
    }

    /**
     * Find all collectible items not in collections.json
     */
    public void checkMissingItems() {
        Set<String> allItemsInCollections = new HashSet<>();

        // Gather all items from collections
        for (CollectionDefinition collection : collectionLoader.getCollections().values()) {
            allItemsInCollections.addAll(collection.getItems());
        }

        // Get all valid collectible materials
        List<String> missingItems = new ArrayList<>();
        for (Material material : Material.values()) {
            if (isCollectible(material)) {
                String name = material.name();
                if (!allItemsInCollections.contains(name)) {
                    missingItems.add(name);
                }
            }
        }

        // Sort alphabetically
        Collections.sort(missingItems);

        // Output to console
        plugin.getLogger().info("Found " + missingItems.size() + " missing items from collections.json");

        // Write to file
        try {
            File outputFile = new File(plugin.getDataFolder(), "missingItems.json");
            Gson gson = new GsonBuilder().setPrettyPrinting().create();

            try (FileWriter writer = new FileWriter(outputFile)) {
                gson.toJson(missingItems, writer);
            }

            plugin.getLogger().info("✅ Missing items written to: " + outputFile.getAbsolutePath());

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to write missingItems.json: " + e.getMessage());
        }
    }

    /**
     * Check if a material is collectible (exclude technical blocks, air, etc.)
     */
    private boolean isCollectible(Material material) {
        // Exclude non-items
        if (!material.isItem()) {
            return false;
        }

        // Exclude air
        if (material.isAir()) {
            return false;
        }

        // Exclude technical/unobtainable items
        String name = material.name();
        if (name.contains("WALL_") && !name.endsWith("_WALL")) {
            return false;
        } // Wall variants
        if (name.startsWith("POTTED_")) {
            return false;
        } // Potted plants
        if (name.contains("FROSTED_ICE")) {
            return false;
        }
        if (name.contains("FIRE") && !name.endsWith("_CHARGE") && !material.equals(Material.FIRE_CHARGE)) {
            return false;
        }
        if (name.equals("WATER") || name.equals("LAVA")) {
            return false;
        }
        if (name.equals("CAVE_AIR") || name.equals("VOID_AIR")) {
            return false;
        }
        if (name.contains("COMMAND_BLOCK")) {
            return false;
        }
        if (name.contains("BARRIER")) {
            return false;
        }
        if (name.contains("STRUCTURE_BLOCK")) {
            return false;
        }
        if (name.contains("JIGSAW")) {
            return false;
        }
        if (name.contains("SPAWNER")) {
            return false;
        }
        if (name.equals("BEDROCK")) {
            return false;
        }
        if (name.equals("END_PORTAL_FRAME")) {
            return false;
        }
        if (name.equals("DEBUG_STICK")) {
            return false;
        }
        if (name.equals("KNOWLEDGE_BOOK")) {
            return false;
        }

        return true;
    }
}
