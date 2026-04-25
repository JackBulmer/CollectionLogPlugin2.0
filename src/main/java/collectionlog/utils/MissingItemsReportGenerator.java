package collectionlog.utils;

import collectionlog.collections.CollectionDefinition;
import collectionlog.collections.CollectionLoader;
import collectionlog.collections.SpecialCollectionLoader;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

/**
 * Generates a report of Minecraft materials not tracked in any collection.
 */
public class MissingItemsReportGenerator {

    private static final Set<String> TECHNICAL_ITEMS = Set.of(
            "BARRIER", "LIGHT", "STRUCTURE_VOID", "STRUCTURE_BLOCK",
            "COMMAND_BLOCK", "CHAIN_COMMAND_BLOCK", "REPEATING_COMMAND_BLOCK",
            "DEBUG_STICK", "JIGSAW", "KNOWLEDGE_BOOK", "BUNDLE",
            "SPAWNER", "TRIAL_SPAWNER"
    );

    private static final Set<String> HARD_EXCLUDE = Set.of(
            "AIR", "CAVE_AIR", "VOID_AIR",
            "WATER", "LAVA",
            "FIRE", "SOUL_FIRE",
            "PISTON_HEAD", "MOVING_PISTON",
            "FROSTED_ICE", "END_PORTAL", "NETHER_PORTAL",
            "BEDROCK"
    );

    private final Plugin plugin;
    private final CollectionLoader collectionLoader;
    private final SpecialCollectionLoader specialCollectionLoader;

    public MissingItemsReportGenerator(Plugin plugin, CollectionLoader collectionLoader,
                                       SpecialCollectionLoader specialCollectionLoader) {
        this.plugin = plugin;
        this.collectionLoader = collectionLoader;
        this.specialCollectionLoader = specialCollectionLoader;
    }

    /**
     * Generate the missing items report and write it to missing_items.txt.
     *
     * @return the output file, or null if generation failed
     */
    public File generate() {
        Set<String> inCollections = collectAllItemSignatures();

        List<String> missingBlocks = new ArrayList<>();
        List<String> missingItems = new ArrayList<>();
        List<String> missingTechnical = new ArrayList<>();
        int totalConsidered = 0;

        for (Material material : Material.values()) {
            String name = material.name();

            if (HARD_EXCLUDE.contains(name) || !material.isItem() || material.isAir()) {
                continue;
            }

            if (isHardExcluded(name)) {
                continue;
            }

            totalConsidered++;

            if (inCollections.contains(name)) {
                continue;
            }

            if (TECHNICAL_ITEMS.contains(name)) {
                missingTechnical.add(name);
            } else if (material.isBlock()) {
                missingBlocks.add(name);
            } else {
                missingItems.add(name);
            }
        }

        Collections.sort(missingBlocks);
        Collections.sort(missingItems);
        Collections.sort(missingTechnical);

        int totalMissing = missingBlocks.size() + missingItems.size() + missingTechnical.size();
        int inCollectionsCount = totalConsidered - totalMissing;
        double coveragePct = totalConsidered > 0
                ? (inCollectionsCount * 100.0 / totalConsidered)
                : 0.0;

        File outputFile = new File(plugin.getDataFolder(), "missing_items.txt");
        plugin.getDataFolder().mkdirs();

        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
            String timestamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            DecimalFormat pctFormat = new DecimalFormat("0.0");
            DecimalFormat numFormat = new DecimalFormat("#,###");

            writer.println("Missing Items Report");
            writer.println("Generated: " + timestamp);
            writer.println("================================================================");
            writer.println("Total Minecraft Items: " + numFormat.format(totalConsidered));
            writer.println("Items in Collections: " + numFormat.format(inCollectionsCount));
            writer.println("Missing Items: " + numFormat.format(totalMissing));
            writer.println("Coverage: " + pctFormat.format(coveragePct) + "%");
            writer.println();
            writer.println("MISSING ITEMS");
            writer.println("================================================================");

            writer.println();
            writer.println("[BLOCKS]");
            for (String name : missingBlocks) {
                writer.println("- " + name);
            }

            writer.println();
            writer.println("[ITEMS]");
            for (String name : missingItems) {
                writer.println("- " + name);
            }

            writer.println();
            writer.println("[TECHNICAL - Consider Excluding]");
            for (String name : missingTechnical) {
                writer.println("- " + name);
            }

            writer.println();
            writer.println("================================================================");
            writer.println("End of Report");

        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to write missing_items.txt", e);
            return null;
        }

        return outputFile;
    }

    /**
     * Collect all item signatures from every loaded collection (standard and special).
     */
    private Set<String> collectAllItemSignatures() {
        Set<String> signatures = new HashSet<>();

        for (CollectionDefinition collection : collectionLoader.getCollections().values()) {
            signatures.addAll(collection.getItems());
        }

        for (CollectionDefinition collection : specialCollectionLoader.getSpecialCollections().values()) {
            signatures.addAll(collection.getItems());
        }

        return signatures;
    }

    /**
     * Additional hard-exclude checks that cannot be expressed as a simple set lookup.
     */
    private boolean isHardExcluded(String name) {
        // Wall-mounted variants that duplicate a placeable block (e.g. OAK_WALL_SIGN vs OAK_SIGN)
        if (name.contains("WALL_") && !name.endsWith("_WALL")) {
            return true;
        }
        // Potted plant blocks have no item form meaningful for collection
        if (name.startsWith("POTTED_")) {
            return true;
        }
        return false;
    }
}
