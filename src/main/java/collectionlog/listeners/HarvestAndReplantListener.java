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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Listener for the Harvest & Auto-Replant perk.
 * Automatically replants crops when broken with a hoe.
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

        // Check if block is player-placed (skip if owned)
        if (blockOwnershipDAO.getOwner(block.getLocation()) != null) {
            return;
        }

        // Check GriefPrevention permissions
        if (!GriefPreventionUtil.canBreak(player, block.getLocation(), blockType)) {
            return;
        }

        // Cancel the default drop behaviour and handle it ourselves
        event.setDropItems(false);

        // Handle harvest and replant
        harvestAndReplant(player, block, tool);
    }

    /**
     * Harvest the crop and automatically replant it.
     */
    private void harvestAndReplant(Player player, Block block, ItemStack tool) {
        Material cropType = block.getType();
        Material seedType = CROP_TO_SEED.get(cropType);

        // Get drops with Fortune enchantment applied
        Collection<ItemStack> drops = block.getDrops(tool, player);

        // Set block to air first (crop is broken)
        block.setType(Material.AIR);

        // Try to find a seed/item to use for replanting
        boolean replanted = false;
        List<ItemStack> playerDrops = new ArrayList<>();

        for (ItemStack drop : drops) {
            if (!replanted && drop.getType() == seedType && drop.getAmount() > 0) {
                // Use 1 seed/item for replanting.
                // If only 1 item drops, it is fully consumed for replanting and the player
                // receives nothing from it (e.g. Pitcher Plant: 1 pod → 0 to player).
                // This is intentional: the replant cost is one seed/item from the harvest.
                if (drop.getAmount() > 1) {
                    drop.setAmount(drop.getAmount() - 1);
                    playerDrops.add(drop);
                }
                // Replant the crop at age 0
                block.setType(cropType);
                BlockData blockData = block.getBlockData();
                if (blockData instanceof Ageable ageable) {
                    ageable.setAge(0);
                    block.setBlockData(ageable);
                }
                replanted = true;
            } else {
                playerDrops.add(drop);
            }
        }

        // Drop remaining items for player
        Location dropLoc = block.getLocation().add(0.5, 0.5, 0.5);
        for (ItemStack drop : playerDrops) {
            block.getWorld().dropItemNaturally(dropLoc, drop);
        }

        // Visual and audio effects when replanted
        if (replanted) {
            // Happy villager particles (green sparkles, used to signal successful replanting)
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

            // Hoe sweep animation (like sword sweep attack)
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
}
