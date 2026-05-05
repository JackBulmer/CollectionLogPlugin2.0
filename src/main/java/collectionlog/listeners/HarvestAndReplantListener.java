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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listener for the Harvest & Auto-Replant perk.
 * Harvests a 5x5 area of fully grown crops and automatically replants them.
 */
public class HarvestAndReplantListener implements Listener {

    private static final String PERK_ID = "harvest_and_replant";

    private final CollectionLogPlugin plugin;
    private final PerkManager perkManager;
    private final BlockOwnershipDAO blockOwnershipDAO;

    // Track protected crop locations with their replant timestamps (500ms immunity)
    private final Map<Location, Long> protectedCrops = new ConcurrentHashMap<>();

    // Supported crop types
    private static final Set<Material> SUPPORTED_CROPS = Set.of(
            Material.WHEAT,
            Material.CARROTS,
            Material.POTATOES,
            Material.BEETROOTS,
            Material.NETHER_WART,
            Material.TORCHFLOWER_CROP,
            Material.TORCHFLOWER,
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
            Material.TORCHFLOWER, Material.TORCHFLOWER_SEEDS,
            Material.PITCHER_CROP, Material.PITCHER_POD
    );

    public HarvestAndReplantListener(CollectionLogPlugin plugin) {
        this.plugin = plugin;
        this.perkManager = plugin.getPerkManager();
        this.blockOwnershipDAO = plugin.getBlockOwnershipDAO();
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();

        // FIRST: Check if this crop is protected (freshly replanted)
        Location blockLocation = block.getLocation();
        Long protectTime = protectedCrops.get(blockLocation);
        if (protectTime != null) {
            if (System.currentTimeMillis() - protectTime < 500) {
                event.setCancelled(true);
                return; // Silently prevent breaking
            } else {
                // Protection expired, remove from map
                protectedCrops.remove(blockLocation);
            }
        }

        Player player = event.getPlayer();
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

        // For PITCHER_CROP, normalise to the bottom block so the 5x5 scan
        // centres on the correct Y level regardless of which half the player broke.
        if (blockType == Material.PITCHER_CROP) {
            BlockData bd = block.getBlockData();
            if (bd instanceof org.bukkit.block.data.Bisected bisected
                    && bisected.getHalf() == org.bukkit.block.data.Bisected.Half.TOP) {
                block = block.getRelative(0, -1, 0);
            }
        }

        // Check if crop is fully grown
        if (!isFullyGrown(block)) {
            return;
        }

        // Skip if the crop was placed by another player (allow own crops and natural crops)
        if (isOwnedByOtherPlayer(block.getLocation(), playerId)) {
            return;
        }

        // Check GriefPrevention permissions for center block
        if (!GriefPreventionUtil.canBreak(player, block.getLocation(), blockType)) {
            return;
        }

        // Cancel the original event – we handle all block breaking manually
        event.setCancelled(true);

        // Scan 5x5 area and batch harvest all fully grown crops
        harvestArea(player, block, tool, playerId);
    }

    /**
     * Scan a 5x5 area around the center block, harvest all fully grown crops,
     * pool the seeds, replant where possible, and drop remaining items.
     */
    private void harvestArea(Player player, Block centerBlock, ItemStack tool, UUID playerId) {
        // Check once whether the player also has the double_sniffer_flowers perk so that
        // when HarvestAndReplant handles sniffer crops the bonus seed is added to the
        // pool (allowing it to be used for replanting).
        boolean hasDoubleSnifferFlowers = perkManager.hasUnlocked(playerId, "double_sniffer_flowers");

        List<CropHarvest> cropsToHarvest = new ArrayList<>();

        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                Block candidate = centerBlock.getRelative(x, 0, z);
                Material type = candidate.getType();

                if (!SUPPORTED_CROPS.contains(type)) {
                    continue;
                }

                // Skip the upper half of a two-block-tall pitcher plant – it will be
                // handled as part of the lower-half entry to avoid duplicate harvests.
                if (type == Material.PITCHER_CROP) {
                    BlockData bd = candidate.getBlockData();
                    if (bd instanceof org.bukkit.block.data.Bisected bisected
                            && bisected.getHalf() == org.bukkit.block.data.Bisected.Half.TOP) {
                        continue;
                    }
                }

                if (!isFullyGrown(candidate)) {
                    continue;
                }
                // Skip crops owned by other players; allow own crops and natural crops
                if (isOwnedByOtherPlayer(candidate.getLocation(), player.getUniqueId())) {
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
            Block cropBlock = crop.location.getBlock();

            // For the two-block-tall pitcher plant, explicitly clear the top half
            // before clearing the bottom so neither block is left floating.
            if (crop.cropType == Material.PITCHER_CROP) {
                Block topBlock = cropBlock.getRelative(0, 1, 0);
                if (topBlock.getType() == Material.PITCHER_CROP) {
                    topBlock.setType(Material.AIR);
                }
            }

            cropBlock.setType(Material.AIR);

            Material seedType = CROP_TO_SEED.get(crop.cropType);
            for (ItemStack drop : crop.drops) {
                if (drop.getType() == seedType) {
                    seedPool.merge(seedType, drop.getAmount(), Integer::sum);
                } else {
                    otherDrops.add(drop);
                }
            }

            // If the double_sniffer_flowers perk is also active, add a bonus seed to
            // the pool so it can be used for replanting (or dropped as surplus).
            // For TORCHFLOWER specifically, vanilla drops only gives TORCHFLOWER_SEEDS
            // (not the flower item itself), so also add the TORCHFLOWER flower item
            // to match what DoublSnifferFlowersListener would deliver.
            if (hasDoubleSnifferFlowers && isSnifferCrop(crop.cropType)) {
                seedPool.merge(seedType, 1, Integer::sum);
                if (crop.cropType == Material.TORCHFLOWER) {
                    otherDrops.add(new ItemStack(Material.TORCHFLOWER, 1));
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
                // TORCHFLOWER (mature flower) must be replanted as TORCHFLOWER_CROP (seedling)
                // so it can grow again; replanting the mature block would leave a static flower.
                Material replantType = (crop.cropType == Material.TORCHFLOWER)
                        ? Material.TORCHFLOWER_CROP
                        : crop.cropType;
                cropBlock.setType(replantType);
                BlockData blockData = cropBlock.getBlockData();
                if (blockData instanceof Ageable ageable) {
                    ageable.setAge(0);
                    cropBlock.setBlockData(ageable);
                }
                seedPool.put(seedType, available - 1);
                replantedCount++;

                // Protect this crop for 500ms to prevent accidental re-breaking
                protectedCrops.put(crop.location, System.currentTimeMillis());
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
     *
     * <p>For PITCHER_CROP, age data lives in the <em>top</em> block. When this method
     * is called with the bottom block (the normalised form used everywhere else in this
     * listener), it looks one block up to read the correct age value.</p>
     *
     * <p>For TORCHFLOWER (the mature flower block), it is always considered fully grown
     * since it is not Ageable and represents the final mature state.</p>
     */
    private boolean isFullyGrown(Block block) {
        Material blockType = block.getType();

        // TORCHFLOWER (mature flower) is always "fully grown"
        if (blockType == Material.TORCHFLOWER) {
            return true;
        }

        if (blockType == Material.PITCHER_CROP) {
            // Determine which half this block is and find the top half.
            Block topBlock = block;
            BlockData bd = block.getBlockData();
            if (bd instanceof org.bukkit.block.data.Bisected bisected
                    && bisected.getHalf() == org.bukkit.block.data.Bisected.Half.BOTTOM) {
                Block above = block.getRelative(0, 1, 0);
                if (above.getType() == Material.PITCHER_CROP) {
                    topBlock = above;
                }
            }
            BlockData topData = topBlock.getBlockData();
            return topData instanceof Ageable topAgeable
                    && topAgeable.getAge() == topAgeable.getMaximumAge();
        }

        BlockData blockData = block.getBlockData();
        if (!(blockData instanceof Ageable ageable)) {
            return false;
        }
        return ageable.getAge() == ageable.getMaximumAge();
    }

    /**
     * Returns {@code true} for crop types that are sniffer-related (torchflower and
     * pitcher plant). Used to decide whether to apply the double_sniffer_flowers bonus.
     */
    private boolean isSnifferCrop(Material material) {
        return material == Material.TORCHFLOWER_CROP ||
               material == Material.TORCHFLOWER ||
               material == Material.PITCHER_CROP;
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
     * Returns true if the block at the given location is owned by a player other than {@code currentPlayerId}.
     * Returns false when the block is unowned (natural crop) or owned by the current player.
     */
    private boolean isOwnedByOtherPlayer(Location location, UUID currentPlayerId) {
        UUID owner = blockOwnershipDAO.getOwner(location);
        return owner != null && !owner.equals(currentPlayerId);
    }

    /**
     * Clean up expired protection entries to prevent memory leaks.
     * Called periodically by a scheduled task.
     */
    public void cleanupExpiredProtections() {
        long now = System.currentTimeMillis();
        protectedCrops.entrySet().removeIf(entry -> now - entry.getValue() > 500);
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
