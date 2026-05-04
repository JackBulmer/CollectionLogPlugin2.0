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
 * Harvests a 5x5 area of fully grown crops and automatically replants them.
 */
public class HarvestAndReplantListener implements Listener {

    private static final String PERK_ID = "harvest_and_replant";

    private final CollectionLogPlugin plugin;
    private final PerkManager perkManager;
    private final BlockOwnershipDAO blockOwnershipDAO;

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

        // Creative mode players don't get perk benefits
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        // Check if player has perk unlocked and enabled
        UUID playerId = player.getUniqueId();
        if (!perkManager.hasUnlocked(playerId, PERK_ID) || !perkManager.isEnabled(playerId, PERK_ID)) {
            return;
        }

        // Must use a hoe
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!isHoe(tool.getType())) {
            return;
        }

        // Check if crop is fully grown
        if (!isFullyGrown(block)) {
            return;
        }

        // Check if center block is player-placed (skip if owned)
        if (blockOwnershipDAO.getOwner(block.getLocation()) != null) {
            return;
        }

        // Check GriefPrevention permissions for center block
        if (!GriefPreventionUtil.canBreak(player, block.getLocation(), blockType)) {
            return;
        }

        // Cancel the original event – we handle all block breaking manually
        event.setCancelled(true);

        // Scan 5x5 area and batch harvest all fully grown crops
        harvestArea(player, block, tool);
    }

    /**
     * Scan a 5x5 area around the center block, harvest all fully grown crops,
     * pool the seeds, replant where possible, and drop remaining items.
     */
    private void harvestArea(Player player, Block centerBlock, ItemStack tool) {
        List<CropHarvest> cropsToHarvest = new ArrayList<>();

        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                Block candidate = centerBlock.getRelative(x, 0, z);
                Material type = candidate.getType();

                if (!SUPPORTED_CROPS.contains(type)) {
                    continue;
                }
                if (!isFullyGrown(candidate)) {
                    continue;
                }
                if (blockOwnershipDAO.getOwner(candidate.getLocation()) != null) {
                    continue;
                }
                if (!GriefPreventionUtil.canBreak(player, candidate.getLocation(), type)) {
                    continue;
                }

                Collection<ItemStack> drops = candidate.getDrops(tool, player);
                cropsToHarvest.add(new CropHarvest(candidate.getLocation(), type, drops));
            }
        }

        if (cropsToHarvest.isEmpty()) {
            return;
        }

        // Break all collected crops immediately (no events triggered)
        Map<Material, Integer> seedPool = new HashMap<>();
        List<ItemStack> otherDrops = new ArrayList<>();

        for (CropHarvest crop : cropsToHarvest) {
            crop.location.getBlock().setType(Material.AIR);

            Material seedType = CROP_TO_SEED.get(crop.cropType);
            for (ItemStack drop : crop.drops) {
                if (drop.getType() == seedType) {
                    seedPool.merge(seedType, drop.getAmount(), Integer::sum);
                } else {
                    otherDrops.add(drop);
                }
            }
        }

        // Replant crops from the pooled seeds
        int replantedCount = 0;
        for (CropHarvest crop : cropsToHarvest) {
            Material seedType = CROP_TO_SEED.get(crop.cropType);
            int available = seedPool.getOrDefault(seedType, 0);

            if (available > 0) {
                Block cropBlock = crop.location.getBlock();
                cropBlock.setType(crop.cropType);
                BlockData blockData = cropBlock.getBlockData();
                if (blockData instanceof Ageable ageable) {
                    ageable.setAge(0);
                    cropBlock.setBlockData(ageable);
                }
                seedPool.put(seedType, available - 1);
                replantedCount++;
            }
        }

        // Drop leftover seeds for the player
        Location dropLoc = centerBlock.getLocation().add(0.5, 0.5, 0.5);
        for (Map.Entry<Material, Integer> entry : seedPool.entrySet()) {
            if (entry.getValue() > 0) {
                centerBlock.getWorld().dropItemNaturally(dropLoc, new ItemStack(entry.getKey(), entry.getValue()));
            }
        }

        // Drop other items (wheat, beetroot, etc.)
        for (ItemStack drop : otherDrops) {
            centerBlock.getWorld().dropItemNaturally(dropLoc, drop);
        }

        // Visual and audio effects
        if (replantedCount > 0) {
            centerBlock.getWorld().spawnParticle(
                    Particle.HAPPY_VILLAGER,
                    centerBlock.getLocation().add(0.5, 0.5, 0.5),
                    15,
                    0.5, 0.5, 0.5,
                    0.0
            );

            centerBlock.getWorld().playSound(
                    centerBlock.getLocation(),
                    Sound.ITEM_CROP_PLANT,
                    0.8f,
                    1.0f
            );

            playSweepAnimation(player);
        }
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

    /**
     * Stores information about a crop to be harvested.
     */
    private static class CropHarvest {
        final Location location;
        final Material cropType;
        final Collection<ItemStack> drops;

        CropHarvest(Location location, Material cropType, Collection<ItemStack> drops) {
            this.location = location;
            this.cropType = cropType;
            this.drops = drops;
        }
    }
}
