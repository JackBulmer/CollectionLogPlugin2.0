package collectionlog.listeners;

import collectionlog.CollectionLogPlugin;
import collectionlog.database.dao.BlockOwnershipDAO;
import collectionlog.perks.PerkManager;
import collectionlog.utils.GriefPreventionUtil;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Listener for the Harvest & Auto-Replant perk.
 * Automatically replants crops when broken with a hoe.
 */
public class HarvestAndReplantListener implements Listener {

    private static final String PERK_ID = "harvest_and_replant";

    private final CollectionLogPlugin plugin;
    private final PerkManager perkManager;
    private final BlockOwnershipDAO blockOwnershipDAO;
    // Replace the Set with a Map
    private final Map<Location, Long> recentlyReplanted = new HashMap<>();

    // Supported crop types
    private static final Set<Material> SUPPORTED_CROPS = Set.of(
            Material.WHEAT,
            Material.CARROTS,
            Material.POTATOES,
            Material.BEETROOTS,
            Material.NETHER_WART,
            Material.TORCHFLOWER_CROP,
            Material.PITCHER_CROP
    );

    // Crop to seed/item mapping (item to consume for replanting)
    private static final Map<Material, Material> CROP_TO_SEED = Map.of(
            Material.WHEAT, Material.WHEAT_SEEDS,
            Material.CARROTS, Material.CARROT,
            Material.POTATOES, Material.POTATO,
            Material.BEETROOTS, Material.BEETROOT_SEEDS,
            Material.NETHER_WART, Material.NETHER_WART,
            Material.TORCHFLOWER_CROP, Material.TORCHFLOWER_SEEDS,
            Material.PITCHER_CROP, Material.PITCHER_POD
    );

    public HarvestAndReplantListener(CollectionLogPlugin plugin) {
        this.plugin = plugin;
        this.perkManager = plugin.getPerkManager();
        this.blockOwnershipDAO = plugin.getBlockOwnershipDAO();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material blockType = block.getType();

        // Only process supported crops
        if (!SUPPORTED_CROPS.contains(blockType)) {
            return;
        }

        // Check if this block was just replanted (prevent double-processing)
        Long replantTime = recentlyReplanted.get(block.getLocation());
        if (replantTime != null && System.currentTimeMillis() - replantTime < 100) {
            return;
        }

        plugin.getLogger().info("=== Crop Break Event ===");
        plugin.getLogger().info("Player: " + player.getName());
        plugin.getLogger().info("Block: " + blockType);
        plugin.getLogger().info("Location: " + block.getLocation());

        // Creative mode players don't get perk benefits
        if (player.getGameMode() == GameMode.CREATIVE) {
            plugin.getLogger().info("SKIP: Creative mode");
            return;
        }

        // Check if player has perk unlocked and enabled
        UUID playerId = player.getUniqueId();
        if (!perkManager.hasUnlocked(playerId, PERK_ID)) {
            plugin.getLogger().info("SKIP: Perk not unlocked");
            return;
        }

        if (!perkManager.isEnabled(playerId, PERK_ID)) {
            plugin.getLogger().info("SKIP: Perk not enabled");
            return;
        }

        plugin.getLogger().info("Perk is unlocked and enabled");

        // Must use a hoe
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!isHoe(tool.getType())) {
            plugin.getLogger().info("SKIP: Not using a hoe (using " + tool.getType() + ")");
            return;
        }

        plugin.getLogger().info("Using hoe: " + tool.getType());

        // Check if crop is fully grown
        if (!isFullyGrown(block)) {
            plugin.getLogger().info("SKIP: Crop not fully grown");
            return;
        }

        plugin.getLogger().info("Crop is fully grown");

        // Check if block is player-placed (skip if owned)
        if (blockOwnershipDAO.getOwner(block.getLocation()) != null) {
            plugin.getLogger().info("SKIP: Block is player-placed (owned)");
            return;
        }

        plugin.getLogger().info("Block is not player-placed");

        // Check GriefPrevention permissions
        if (!GriefPreventionUtil.canBreak(player, block.getLocation(), blockType)) {
            plugin.getLogger().info("SKIP: GriefPrevention denied");
            return;
        }

        plugin.getLogger().info("GriefPrevention check passed");

        // CANCEL THE ENTIRE EVENT - we handle everything ourselves
        event.setCancelled(true);

        // Mark this location with timestamp
        recentlyReplanted.put(block.getLocation(), System.currentTimeMillis());

        // Clean up old entries after 5 seconds
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            recentlyReplanted.remove(block.getLocation());
        }, 100L);

        plugin.getLogger().info("Event cancelled, calling harvestAndReplant");

        // Handle harvest and replant
        harvestAndReplant(player, block, tool);
    }

    /**
     * Harvest the crop and automatically replant it.
     */
    private void harvestAndReplant(Player player, Block block, ItemStack tool) {
        Material cropType = block.getType();
        Material seedType = CROP_TO_SEED.get(cropType);

        plugin.getLogger().info("=== Harvest Debug ===");
        plugin.getLogger().info("Player: " + player.getName());
        plugin.getLogger().info("Crop Type: " + cropType);
        plugin.getLogger().info("Seed Type: " + seedType);
        plugin.getLogger().info("Block location: " + block.getLocation());

        // Get drops with Fortune enchantment applied
        Collection<ItemStack> drops = block.getDrops(tool, player);

        plugin.getLogger().info("Drops received: " + drops.size() + " stacks");
        for (ItemStack drop : drops) {
            plugin.getLogger().info("  - " + drop.getType() + " x" + drop.getAmount());
        }

        // Combine all drops of the seed type
        int totalSeeds = 0;
        List<ItemStack> otherDrops = new ArrayList<>();

        for (ItemStack drop : drops) {
            if (drop.getType() == seedType) {
                totalSeeds += drop.getAmount();
            } else {
                otherDrops.add(drop);
            }
        }

        plugin.getLogger().info("Total seeds/items: " + totalSeeds);
        plugin.getLogger().info("Other drops: " + otherDrops.size());

        // Set block to air first (crop is broken)
        block.setType(Material.AIR);
        plugin.getLogger().info("Block set to AIR");

        // Check if we have at least 1 seed/item to replant
        boolean replanted = false;
        if (totalSeeds > 0) {
            plugin.getLogger().info("Attempting replant...");

            // Use 1 seed/item for replanting
            totalSeeds--;

            // Replant the crop at age 0
            block.setType(cropType);
            plugin.getLogger().info("Block set back to: " + cropType);

            BlockData blockData = block.getBlockData();
            plugin.getLogger().info("BlockData type: " + blockData.getClass().getSimpleName());

            if (blockData instanceof Ageable ageable) {
                plugin.getLogger().info("Max age: " + ageable.getMaximumAge());
                ageable.setAge(0);
                block.setBlockData(ageable);
                plugin.getLogger().info("Age set to 0 and blockData applied");
                replanted = true;
            } else {
                plugin.getLogger().warning("BlockData is NOT Ageable!");
            }
        } else {
            plugin.getLogger().warning("No seeds available for replanting!");
        }

        plugin.getLogger().info("Replanted: " + replanted);
        plugin.getLogger().info("Final block type: " + block.getType());

        // Check what's actually there after a tick
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            plugin.getLogger().info("Block after 1 tick: " + block.getType());
        }, 1L);

        // Drop remaining items for player
        Location dropLoc = block.getLocation().add(0.5, 0.5, 0.5);

        // Drop remaining seeds (if any)
        if (totalSeeds > 0) {
            block.getWorld().dropItemNaturally(dropLoc, new ItemStack(seedType, totalSeeds));
        }

        // Drop other items (wheat, beetroot, etc.)
        for (ItemStack drop : otherDrops) {
            block.getWorld().dropItemNaturally(dropLoc, drop);
        }

        // Visual and audio effects when replanted
        if (replanted) {
            // Happy villager particles
            block.getWorld().spawnParticle(
                    Particle.HAPPY_VILLAGER,
                    block.getLocation().add(0.5, 0.5, 0.5),
                    15,
                    0.5, 0.5, 0.5,
                    0.0
            );

            // Crop planting sound
            block.getWorld().playSound(
                    block.getLocation(),
                    Sound.ITEM_CROP_PLANT,
                    0.8f,
                    1.0f
            );

            // Hoe sweep animation
            playSweepAnimation(player);
        }

        plugin.getLogger().info("=== End Harvest Debug ===");
    }

    /**
     * Play a sweep animation for the hoe (like sword sweep attack).
     */
    private void playSweepAnimation(Player player) {
        Location loc = player.getLocation().add(
                player.getLocation().getDirection().multiply(1.5)
        );
        loc.setY(player.getLocation().getY() + 1.0);

        player.getWorld().spawnParticle(
                Particle.SWEEP_ATTACK,
                loc,
                1
        );

        player.getWorld().playSound(
                player.getLocation(),
                Sound.ENTITY_PLAYER_ATTACK_SWEEP,
                0.6f,
                1.0f
        );
    }

    /**
     * Check if a crop is fully grown.
     */
    private boolean isFullyGrown(Block block) {
        BlockData blockData = block.getBlockData();
        if (!(blockData instanceof Ageable ageable)) {
            return false;
        }
        return ageable.getAge() == ageable.getMaximumAge();
    }

    /**
     * Check if a material is a hoe.
     */
    private boolean isHoe(Material material) {
        return switch (material) {
            case WOODEN_HOE, STONE_HOE, IRON_HOE,
                 GOLDEN_HOE, DIAMOND_HOE, NETHERITE_HOE -> true;
            default -> false;
        };
    }
}
