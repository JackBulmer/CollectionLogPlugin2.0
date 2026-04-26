package collectionlog.perks;

import org.bukkit.Material;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Central registry of every perk definition in the plugin.
 * Perks are registered here with their display properties; actual gameplay
 * behaviour is wired up in dedicated listener/handler classes added in
 * subsequent PRs.
 */
public final class PerkRegistry {

    private static final Map<String, Perk> PERKS = new LinkedHashMap<>();

    static {
        // ── Non-potion perks ─────────────────────────────────────────────────

        register("toggle_phantoms", "Toggle Phantoms",
                List.of("§7Prevents phantoms from spawning for you",
                        "§7when the perk is enabled."),
                Material.PHANTOM_MEMBRANE, "beds", true, false);

        register("infinite_lava_bucket", "Infinite Lava Bucket",
                List.of("§7Receive a lava bucket that never empties,",
                        "§7regardless of placement or use as fuel."),
                Material.LAVA_BUCKET, "buckets", false, false);

        register("glowing", "Glowing",
                List.of("§7Choose a colour to glow in.",
                        "§7Use §e/glowing §7to pick your colour."),
                Material.GLOWSTONE_DUST, "candles", true, false);

        register("craftable_concrete", "Craftable Concrete",
                List.of("§7Craft concrete by surrounding a water bucket",
                        "§7with concrete powder (outputs 8 per craft)."),
                Material.WHITE_CONCRETE, "concrete", false, false);

        register("infinite_dyes", "Infinite Dyes",
                List.of("§7Open a GUI to change the colour of dyeable",
                        "§7items without consuming dyes."),
                Material.YELLOW_DYE, "dyes", false, false);

        register("extra_heart", "Extra Heart",
                List.of("§7Permanently gain an extra heart of health."),
                Material.NETHER_STAR, null, false, false);

        register("end_teleportation", "End Teleportation",
                List.of("§7Marks your first-ever End entry coordinates.",
                        "§7Use §e/cl teleport end §7to return there."),
                Material.ENDER_EYE, "end", false, false);

        register("cheaper_golden_apples", "Cheaper Golden Apples",
                List.of("§7Craft golden apples using 4 gold ingots",
                        "§7(skip the corners) instead of 8."),
                Material.GOLDEN_APPLE, "food", false, false);

        register("instant_glass_smelting", "Instant Glass Smelting",
                List.of("§7Mining sand drops glass directly",
                        "§7instead of sand while this perk is enabled."),
                Material.GLASS, "glass", true, false);

        register("fast_ghast_riding", "Quicker Happy Ghasts",
                List.of("§7Travel faster when riding a Happy Ghast."),
                Material.DRIED_GHAST, "happy ghast", true, false);

        register("quicker_potion_brewing", "Quicker Potion Brewing",
                List.of("§7Reduces the time taken to brew potions in a",
                        "§7brewing stand."),
                Material.BREWING_STAND, "potion ingredients", true, false);

        register("openable_shulkers", "Openable Shulkers",
                List.of("§7Open shulker boxes without placing them",
                        "§7on the ground."),
                Material.PURPLE_SHULKER_BOX, "shulker", true, false);

        register("double_sniffer_flowers", "Double Sniffer Flowers",
                List.of("§7Receive both the seeds and the flower",
                        "§7when harvesting sniffer drops."),
                Material.PITCHER_POD, "sniffer", false, false);

        register("tunnel_miner", "Tunnel Miner",
                List.of("§7Dig a 3×3 tunnel in the direction you face",
                        "§7using a pickaxe or shovel.",
                        "§7Auto-disables after 5 minutes of inactivity."),
                Material.IRON_PICKAXE, "stones", true, false);

        register("permanent_bad_omen", "Permanent Bad Omen",
                List.of("§7Apply Bad Omen IV to yourself on demand.",
                        "§7Automatically restored after raids and omen events.",
                        "§7Auto-disables after 5 minutes of inactivity."),
                Material.OMINOUS_BOTTLE, "trails", true, false);

        register("craft_command", "Craft Command",
                List.of("§7Use §e/craft §7to open a portable crafting",
                        "§7table. Respects crafting collection logic."),
                Material.CRAFTING_TABLE, "utilities", false, false);

        register("shorter_strafe_cooldown", "Shorter Strafe Cooldown",
                List.of("§7Reduces your strafe cooldown from",
                        "§75 seconds to 2 seconds."),
                Material.SHIELD, "weapons and tools", false, false);

        register("tree_capitator", "Tree Capitator",
                List.of("§7Break the bottom log of a tree to fell",
                        "§7the entire tree automatically.",
                        "§7Auto-disables after 5 minutes of inactivity."),
                Material.IRON_AXE, "wood", true, false);

        register("vein_miner", "Vein Miner",
                List.of("§7Break an entire ore vein at once with a",
                        "§7single block break."),
                Material.DIAMOND_PICKAXE, "ores:ore blocks", true, false);

        register("harvest_and_replant", "Harvest & Auto-Replant",
                List.of("§7Harvesting crops with a hoe collects all",
                        "§7fully grown crops within 4 blocks and replants.",
                        "§7Respects the Double Sniffer Flowers perk."),
                Material.DIAMOND_HOE, "ores:hoes", true, false);

        // ── Potion-effect perks ───────────────────────────────────────────────

        register("permanent_slow_falling", "Permanent Slow Falling",
                List.of("§7Permanently applies the Slow Falling effect."),
                Material.FEATHER, "end", true, true);

        register("permanent_fire_resistance", "Permanent Fire Resistance",
                List.of("§7Permanently applies the Fire Resistance effect."),
                Material.FIRE_CHARGE, "nether", true, true);

        register("permanent_dolphins_grace", "Permanent Dolphin's Grace",
                List.of("§7Permanently applies the Dolphin's Grace effect."),
                Material.HEART_OF_THE_SEA, "jb_fishing_plus", true, true);

        register("permanent_water_breathing", "Permanent Water Breathing",
                List.of("§7Permanently applies the Water Breathing effect."),
                Material.PUFFERFISH, "ocean", true, true);

        register("permanent_night_vision", "Permanent Night Vision",
                List.of("§7Permanently applies the Night Vision effect."),
                Material.GOLDEN_CARROT, "ores:ore materials", true, true);
    }

    private static void register(String id, String name, List<String> description,
                                 Material icon, String requiredCollection,
                                 boolean toggleable, boolean potionEffect) {
        PERKS.put(id, new Perk(id, name, description, icon, requiredCollection, toggleable, potionEffect));
    }

    /** Returns the perk with the given ID, or null if unknown. */
    public static Perk getPerk(String id) {
        return PERKS.get(id);
    }

    /** Returns an unmodifiable view of all registered perks. */
    public static Collection<Perk> getAllPerks() {
        return Collections.unmodifiableCollection(PERKS.values());
    }

    /** Returns all perks that are NOT potion-effect perks. */
    public static List<Perk> getNonPotionPerks() {
        return PERKS.values().stream()
                .filter(p -> !p.isPotionEffect())
                .collect(Collectors.toList());
    }

    /** Returns all perks that are potion-effect perks. */
    public static List<Perk> getPotionEffectPerks() {
        return PERKS.values().stream()
                .filter(Perk::isPotionEffect)
                .collect(Collectors.toList());
    }

    private PerkRegistry() {
    }
}
