package collectionlog.listeners;

import collectionlog.CollectionLogPlugin;
import collectionlog.managers.StrafeManager;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Allows players to strafe by right-clicking with a sword.
 * Boosts player based on movement direction with an upward burst.
 */
public class StrafeListener implements Listener {
    private static final double MIN_HORIZONTAL_MOVEMENT_SQUARED = 0.01;

    private final CollectionLogPlugin plugin;
    private final StrafeManager strafeManager;
    private final Map<UUID, Long> airborneTime = new HashMap<>();

    // Interactable blocks that prevent strafing
    private static final Set<Material> INTERACTABLE_BLOCKS = Set.of(
            Material.CRAFTING_TABLE, Material.CHEST, Material.ENDER_CHEST, Material.TRAPPED_CHEST,
            Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER,
            Material.ANVIL, Material.CHIPPED_ANVIL, Material.DAMAGED_ANVIL,
            Material.ENCHANTING_TABLE, Material.BREWING_STAND, Material.SMITHING_TABLE,
            Material.STONECUTTER, Material.LOOM, Material.CARTOGRAPHY_TABLE, Material.GRINDSTONE,
            Material.BARREL, Material.SHULKER_BOX, Material.HOPPER, Material.DROPPER, Material.DISPENSER,
            Material.LECTERN, Material.BEACON, Material.RESPAWN_ANCHOR,
            // Doors
            Material.OAK_DOOR, Material.SPRUCE_DOOR, Material.BIRCH_DOOR, Material.JUNGLE_DOOR,
            Material.ACACIA_DOOR, Material.DARK_OAK_DOOR, Material.MANGROVE_DOOR, Material.CHERRY_DOOR,
            Material.BAMBOO_DOOR, Material.CRIMSON_DOOR, Material.WARPED_DOOR, Material.IRON_DOOR,
            // Trapdoors
            Material.OAK_TRAPDOOR, Material.SPRUCE_TRAPDOOR, Material.BIRCH_TRAPDOOR,
            Material.JUNGLE_TRAPDOOR, Material.ACACIA_TRAPDOOR, Material.DARK_OAK_TRAPDOOR,
            Material.MANGROVE_TRAPDOOR, Material.CHERRY_TRAPDOOR, Material.BAMBOO_TRAPDOOR,
            Material.CRIMSON_TRAPDOOR, Material.WARPED_TRAPDOOR, Material.IRON_TRAPDOOR,
            // Fence gates
            Material.OAK_FENCE_GATE, Material.SPRUCE_FENCE_GATE, Material.BIRCH_FENCE_GATE,
            Material.JUNGLE_FENCE_GATE, Material.ACACIA_FENCE_GATE, Material.DARK_OAK_FENCE_GATE,
            Material.MANGROVE_FENCE_GATE, Material.CHERRY_FENCE_GATE, Material.BAMBOO_FENCE_GATE,
            Material.CRIMSON_FENCE_GATE, Material.WARPED_FENCE_GATE,
            // Buttons and levers
            Material.STONE_BUTTON, Material.OAK_BUTTON, Material.SPRUCE_BUTTON, Material.BIRCH_BUTTON,
            Material.JUNGLE_BUTTON, Material.ACACIA_BUTTON, Material.DARK_OAK_BUTTON,
            Material.MANGROVE_BUTTON, Material.CHERRY_BUTTON, Material.BAMBOO_BUTTON,
            Material.CRIMSON_BUTTON, Material.WARPED_BUTTON, Material.POLISHED_BLACKSTONE_BUTTON,
            Material.LEVER,
            // Redstone
            Material.REPEATER, Material.COMPARATOR, Material.DAYLIGHT_DETECTOR,
            // Beds
            Material.RED_BED, Material.WHITE_BED, Material.BLACK_BED, Material.BLUE_BED,
            Material.BROWN_BED, Material.CYAN_BED, Material.GRAY_BED, Material.GREEN_BED,
            Material.LIGHT_BLUE_BED, Material.LIGHT_GRAY_BED, Material.LIME_BED, Material.MAGENTA_BED,
            Material.ORANGE_BED, Material.PINK_BED, Material.PURPLE_BED, Material.YELLOW_BED
    );

    public StrafeListener(CollectionLogPlugin plugin, StrafeManager strafeManager) {
        this.plugin = plugin;
        this.strafeManager = strafeManager;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Track airborne time
        if (!player.isOnGround()) {
            if (!airborneTime.containsKey(playerId)) {
                airborneTime.put(playerId, System.currentTimeMillis());
            }

            long timeInAir = System.currentTimeMillis() - airborneTime.get(playerId);

            // Remove fall damage protection after more than 3 seconds in air
            if (timeInAir > 3000) {
                strafeManager.removeFallProtection(playerId);
            }
        } else {
            // Player is on ground, reset airborne time
            airborneTime.remove(playerId);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || !isSword(item.getType())) {
            return;
        }

        // Check if player is looking at an interactable block
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block clickedBlock = event.getClickedBlock();
            if (clickedBlock != null && INTERACTABLE_BLOCKS.contains(clickedBlock.getType())) {
                return; // Allow block interaction, don't strafe
            }
        }

        // Additional check: ray trace for nearby interactable blocks
        RayTraceResult rayTrace = player.rayTraceBlocks(5.0);
        if (rayTrace != null && rayTrace.getHitBlock() != null) {
            if (INTERACTABLE_BLOCKS.contains(rayTrace.getHitBlock().getType())) {
                return; // Block interaction takes priority
            }
        }

        event.setCancelled(true);

        // Check if strafe perk is enabled
        UUID playerId = player.getUniqueId();
        if (!plugin.getPerkManager().isActive(playerId, "strafe")) {
            return; // Strafe perk is disabled
        }

        // Check cooldown
        if (!strafeManager.canStrafe(player)) {
            return;
        }

        // Can't strafe if in air for more than 2 seconds
        Long timeInAir = airborneTime.get(playerId);
        if (timeInAir != null) {
            long duration = System.currentTimeMillis() - timeInAir;
            if (duration > 1000) {
                return; // Been in air too long
            }
        }

        // Perform strafe
        performStrafe(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        strafeManager.cleanupPlayer(playerId);
        airborneTime.remove(playerId);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;

        UUID playerId = player.getUniqueId();

        if (strafeManager.hasFallDamageProtection(playerId)) {
            event.setCancelled(true);
        }
    }

    private boolean isSword(Material material) {
        return material == Material.WOODEN_SWORD ||
               material == Material.STONE_SWORD ||
               material == Material.IRON_SWORD ||
               material == Material.GOLDEN_SWORD ||
               material == Material.DIAMOND_SWORD ||
               material == Material.NETHERITE_SWORD;
    }

    private void performStrafe(Player player) {
        // Get direction player is moving
        Vector velocity = player.getVelocity().clone();
        velocity.setY(0); // Ignore vertical movement

        // If player isn't moving horizontally, use facing direction as fallback
        if (velocity.lengthSquared() < MIN_HORIZONTAL_MOVEMENT_SQUARED) {
            velocity = player.getLocation().getDirection().clone();
            velocity.setY(0);
        }

        if (velocity.lengthSquared() < MIN_HORIZONTAL_MOVEMENT_SQUARED) {
            velocity = new Vector(0, 0, 1);
        }

        velocity.normalize();

        // Boost: 1.0 horizontal velocity, 0.8 vertical velocity
        Vector strafeVelocity = velocity.multiply(1.0);
        strafeVelocity.setY(0.8);

        player.setVelocity(strafeVelocity);
        player.playSound(player.getLocation(), Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.0f, 1.5f);

        strafeManager.useStrafe(player);
    }
}
