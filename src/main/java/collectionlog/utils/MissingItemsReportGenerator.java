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
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;

/**
 * Generates a report of Minecraft materials not tracked in any collection.
 */
public class MissingItemsReportGenerator {

    // Items that are completely unobtainable, creative-only, or don't exist as proper items.
    // These are excluded entirely from the report.
    private static final Set<String> HARD_EXCLUDE_NAMES = Set.of(
            // Non-block internal states
            "AIR", "CAVE_AIR", "VOID_AIR",
            "WATER", "LAVA",
            "FIRE", "SOUL_FIRE",
            "PISTON_HEAD", "MOVING_PISTON",
            "FROSTED_ICE", "END_PORTAL", "NETHER_PORTAL",
            "BEDROCK",
            // Non-existent / unobtainable spawn eggs
            "ENDER_DRAGON_SPAWN_EGG", "WITHER_SPAWN_EGG", "ZOMBIE_HORSE_SPAWN_EGG",
            // Creative-only blocks
            "END_PORTAL_FRAME", "COMMAND_BLOCK_MINECART", "REINFORCED_DEEPSLATE",
            // Technical / creative-only items
            "BARRIER", "STRUCTURE_VOID", "STRUCTURE_BLOCK", "JIGSAW", "LIGHT",
            "DEBUG_STICK", "KNOWLEDGE_BOOK",
            // Command blocks (operator-only, never in survival)
            "COMMAND_BLOCK", "CHAIN_COMMAND_BLOCK", "REPEATING_COMMAND_BLOCK"
    );

    // Blocks obtainable only with Silk Touch — listed separately so they can be
    // reviewed and potentially added to collections without cluttering the main list.
    private static final Set<String> SILK_TOUCH_ITEMS = Set.of(
            "BUDDING_AMETHYST", "SPAWNER", "TRIAL_SPAWNER",
            "INFESTED_STONE", "INFESTED_COBBLESTONE", "INFESTED_STONE_BRICKS",
            "INFESTED_MOSSY_STONE_BRICKS", "INFESTED_CRACKED_STONE_BRICKS",
            "INFESTED_CHISELED_STONE_BRICKS", "INFESTED_DEEPSLATE",
            "FARMLAND", "DIRT_PATH", "SUSPICIOUS_SAND", "SUSPICIOUS_GRAVEL"
    );

    private static final Set<String> WALL_MOUNT_SUFFIXES = Set.of(
            "_WALL_SIGN", "_WALL_BANNER",
            "_WALL_SKULL", "_WALL_HEAD",
            "_WALL_TORCH", "_WALL_FAN"
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
        Set<String> inCollections = collectAllCoveredMaterials();

        List<String> missingBlocks = new ArrayList<>();
        List<String> missingItems = new ArrayList<>();
        List<String> missingSilkTouch = new ArrayList<>();
        int totalConsidered = 0;

        for (Material material : Material.values()) {
            String name = material.name();

            if (!material.isItem() || material.isAir() || isHardExcluded(name)) {
                continue;
            }

            totalConsidered++;

            if (inCollections.contains(name)) {
                continue;
            }

            if (SILK_TOUCH_ITEMS.contains(name)) {
                missingSilkTouch.add(name);
            } else if (material.isBlock()) {
                missingBlocks.add(name);
            } else {
                missingItems.add(name);
            }
        }

        Collections.sort(missingBlocks);
        Collections.sort(missingItems);
        Collections.sort(missingSilkTouch);

        int totalMissing = missingBlocks.size() + missingItems.size() + missingSilkTouch.size();
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
            writer.println("NOTE: This report checks standard collections AND all special");
            writer.println("  collections (enchantments, potions, spawn eggs, mob heads,");
            writer.println("  armour trims, horns). Custom model data / HeadDatabase entries");
            writer.println("  are intentionally excluded from coverage checks.");
            writer.println("NOTE: The following are excluded entirely from this report:");
            writer.println("  - Creative-only items (command blocks, barriers, structure blocks, etc.)");
            writer.println("  - Non-existent/unobtainable spawn eggs (Ender Dragon, Wither, Zombie Horse)");
            writer.println("  - Internal/technical non-item states (air, water, fire, etc.)");
            writer.println("================================================================");
            writer.println("Total Minecraft Items: " + numFormat.format(totalConsidered));
            writer.println("Items in Collections: " + numFormat.format(inCollectionsCount));
            writer.println("Missing Items: " + numFormat.format(totalMissing));
            writer.println("  - Blocks: " + missingBlocks.size());
            writer.println("  - Items: " + missingItems.size());
            writer.println("  - Silk Touch Obtainable: " + missingSilkTouch.size());
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
            writer.println("[SILK TOUCH OBTAINABLE]");
            writer.println("(Blocks only obtainable in survival via Silk Touch enchantment)");
            for (String name : missingSilkTouch) {
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
     * Collect all material names that are covered by any collection (standard or special).
     *
     * <p>Standard collections store items by their Bukkit {@link Material} name, so their
     * entries are added directly. Special collections use custom signature formats
     * (e.g. {@code "SPEED:NORMAL"} for potions, trim names for armour trims), so we
     * translate each special collection type to the set of Material names it covers.
     * Custom-model-data / HeadDatabase entries are intentionally excluded from this
     * mapping because they represent CMD items, not base Material coverage.</p>
     */
    private Set<String> collectAllCoveredMaterials() {
        Set<String> covered = new HashSet<>();

        // Standard collections: items are stored as plain Material names
        for (CollectionDefinition collection : collectionLoader.getCollections().values()) {
            covered.addAll(collection.getItems());
        }

        // Spawn eggs collection: already stores Bukkit Material names (e.g. CREEPER_SPAWN_EGG)
        CollectionDefinition spawnEggs = specialCollectionLoader.getSpecialCollection("spawn_eggs");
        if (spawnEggs != null) {
            covered.addAll(spawnEggs.getItems());
        }

        // Enchantments collection: signatures are "enchantment:level" — covers ENCHANTED_BOOK
        CollectionDefinition enchantments = specialCollectionLoader.getSpecialCollection("enchantments");
        if (enchantments != null && !enchantments.getItems().isEmpty()) {
            covered.add("ENCHANTED_BOOK");
        }

        // Potions collection: signatures are "TYPE:FORMAT" — covers the four potion item types
        CollectionDefinition potions = specialCollectionLoader.getSpecialCollection("potions");
        if (potions != null && !potions.getItems().isEmpty()) {
            covered.add("POTION");
            covered.add("SPLASH_POTION");
            covered.add("LINGERING_POTION");
            covered.add("TIPPED_ARROW");
        }

        // Armour trims collection: each trim name maps to a smithing template Material name
        CollectionDefinition armourTrims = specialCollectionLoader.getSpecialCollection("armour_trims");
        if (armourTrims != null) {
            for (String trimName : armourTrims.getItems()) {
                // e.g. "ward" -> "WARD_ARMOR_TRIM_SMITHING_TEMPLATE"
                covered.add(trimName.toUpperCase(Locale.ROOT) + "_ARMOR_TRIM_SMITHING_TEMPLATE");
            }
        }

        // Horns collection: signatures are instrument keys — covers GOAT_HORN
        CollectionDefinition horns = specialCollectionLoader.getSpecialCollection("horns");
        if (horns != null && !horns.getItems().isEmpty()) {
            covered.add("GOAT_HORN");
        }

        // Mob heads collection: vanilla entries are plain entity names; custom entries are
        // "ENTITY:headDbId" (CMD items). Only vanilla entries map to real skull Materials.
        CollectionDefinition mobHeads = specialCollectionLoader.getSpecialCollection("mob_heads");
        if (mobHeads != null) {
            for (String signature : mobHeads.getItems()) {
                if (!signature.contains(":")) {
                    String material = entityToSkullMaterial(signature);
                    if (material != null) {
                        covered.add(material);
                    }
                }
            }
        }

        return covered;
    }

    /**
     * Maps a vanilla mob entity name (as stored in mob_heads.json) to its Bukkit Material name.
     *
     * @param entityName the entity name from the vanilla section of mob_heads.json
     * @return the corresponding skull/head Material name, or {@code null} if not mapped
     */
    private String entityToSkullMaterial(String entityName) {
        return switch (entityName.toUpperCase(Locale.ROOT)) {
            case "ZOMBIE" -> "ZOMBIE_HEAD";
            case "SKELETON" -> "SKELETON_SKULL";
            case "CREEPER" -> "CREEPER_HEAD";
            case "WITHER_SKELETON" -> "WITHER_SKELETON_SKULL";
            case "ENDER_DRAGON" -> "DRAGON_HEAD";
            case "PIGLIN" -> "PIGLIN_HEAD";
            default -> null;
        };
    }

    /**
     * Returns true if the material should be completely excluded from the report.
     */
    private boolean isHardExcluded(String name) {
        if (HARD_EXCLUDE_NAMES.contains(name)) {
            return true;
        }
        // Wall-mounted variants that duplicate a placeable block (e.g. OAK_WALL_SIGN vs OAK_SIGN)
        for (String suffix : WALL_MOUNT_SUFFIXES) {
            if (name.endsWith(suffix)) {
                return true;
            }
        }
        // Potted plant blocks have no meaningful item form for collection purposes
        if (name.startsWith("POTTED_")) {
            return true;
        }
        return false;
    }
}
