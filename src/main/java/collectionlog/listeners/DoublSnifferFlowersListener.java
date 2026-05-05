package collectionlog.listeners;

import collectionlog.CollectionLogPlugin;
import collectionlog.perks.PerkManager;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Listener for the Double Sniffer Flowers perk.
 * When a player with this perk harvests fully grown sniffer flowers from farmland,
 * they receive both the flower item and a bonus seed.
 *
 * <p>Runs at HIGH priority so it fires after HarvestAndReplantListener (NORMAL), which
 * cancels the event when it handles the crop itself. With ignoreCancelled=true, this
 * listener is automatically skipped when HarvestAndReplant takes over.</p>
 */
public class DoublSnifferFlowersListener implements Listener {

    private static final String PERK_ID = "double_sniffer_flowers";

    private final CollectionLogPlugin plugin;
    private final PerkManager perkManager;

    public DoublSnifferFlowersListener(CollectionLogPlugin plugin) {
        this.plugin = plugin;
        this.perkManager = plugin.getPerkManager();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material blockType = block.getType();

        // DEBUG: Log all block breaks to see what material it actually is
        if (blockType == Material.TORCHFLOWER || blockType == Material.TORCHFLOWER_CROP) {
            plugin.getLogger().info("[DEBUG] Torchflower block broken! Material: " + blockType);
            plugin.getLogger().info("[DEBUG] Block data type: " + block.getBlockData().getClass().getName());
        }

        // Only process sniffer flowers
        if (blockType != Material.TORCHFLOWER_CROP &&
                blockType != Material.TORCHFLOWER &&
                blockType != Material.PITCHER_CROP) {
            return;
        }

        plugin.getLogger().info("[DEBUG] Passed material check for: " + blockType);

        // Creative mode excluded (no drops)
        if (player.getGameMode() == GameMode.CREATIVE) {
            plugin.getLogger().info("[DEBUG] Player in creative mode, skipping");
            return;
        }

        UUID playerId = player.getUniqueId();

        // Check perk unlocked
        if (!perkManager.hasUnlocked(playerId, PERK_ID)) {
            plugin.getLogger().info("[DEBUG] Player " + player.getName() + " doesn't have perk unlocked");
            return;
        }

        plugin.getLogger().info("[DEBUG] Player has perk unlocked, processing...");

        // If the player also has harvest_and_replant active and is using a hoe,
        // defer to HarvestAndReplantListener which handles the full 5x5 harvest
        // and integrates the bonus seed into the replanting pool.
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (perkManager.hasUnlocked(playerId, "harvest_and_replant") &&
                perkManager.isEnabled(playerId, "harvest_and_replant") &&
                tool != null && isHoe(tool.getType())) {
            plugin.getLogger().info("[DEBUG] Player has harvest_and_replant with hoe, deferring to that listener");
            return; // Let HarvestAndReplantListener handle it
        }

        if (blockType == Material.TORCHFLOWER_CROP || blockType == Material.TORCHFLOWER) {
            plugin.getLogger().info("[DEBUG] Handling torchflower...");
            boolean result = handleTorchflower(event, block);
            plugin.getLogger().info("[DEBUG] Torchflower handling result: " + result);
        } else {
            plugin.getLogger().info("[DEBUG] Handling pitcher crop...");
            boolean result = handlePitcherCrop(event, block);
            plugin.getLogger().info("[DEBUG] Pitcher crop handling result: " + result);
        }
    }

    /**
     * Handle torchflower harvesting.
     * Returns {@code true} if perk was applied (vanilla drops cancelled and items
     * dropped manually), {@code false} if conditions are not met (vanilla behaviour
     * is left unchanged).
     *
     * <p>Torchflower has two block types:
     * <ul>
     *   <li>{@code TORCHFLOWER_CROP} – growing crop (Ageable, age 0–1). Only accepted
     *       when at maximum age.</li>
     *   <li>{@code TORCHFLOWER} – mature flower block (not Ageable). Always accepted;
     *       no age check needed.</li>
     * </ul>
     * Both types must be sitting on farmland to qualify for the bonus seed.</p>
     */
    private boolean handleTorchflower(BlockBreakEvent event, Block block) {
        plugin.getLogger().info("[DEBUG] handleTorchflower: Starting checks...");

        Material blockType = block.getType();
        plugin.getLogger().info("[DEBUG] Block type: " + blockType);

        // Handle TORCHFLOWER_CROP (growing crop, Ageable)
        if (blockType == Material.TORCHFLOWER_CROP) {
            plugin.getLogger().info("[DEBUG] Processing TORCHFLOWER_CROP (growing crop)");

            BlockData blockData = block.getBlockData();
            if (!(blockData instanceof Ageable ageable)) {
                plugin.getLogger().info("[DEBUG] TORCHFLOWER_CROP is not Ageable (shouldn't happen)");
                return false;
            }

            plugin.getLogger().info("[DEBUG] Crop age: " + ageable.getAge() + "/" + ageable.getMaximumAge());

            if (ageable.getAge() != ageable.getMaximumAge()) {
                plugin.getLogger().info("[DEBUG] Not fully grown!");
                return false;
            }
        }
        // Handle TORCHFLOWER (mature flower, not Ageable)
        else if (blockType == Material.TORCHFLOWER) {
            plugin.getLogger().info("[DEBUG] Processing TORCHFLOWER (mature flower, always harvestable)");
            // No age check needed – the flower block is always "mature"
        } else {
            plugin.getLogger().info("[DEBUG] Unexpected block type: " + blockType);
            return false;
        }

        Block below = block.getRelative(0, -1, 0);
        plugin.getLogger().info("[DEBUG] Block below: " + below.getType());

        if (below.getType() != Material.FARMLAND) {
            plugin.getLogger().info("[DEBUG] Not on farmland!");
            return false;
        }

        plugin.getLogger().info("[DEBUG] All checks passed! Dropping items...");

        // Cancel vanilla drops – manually drop flower + seed so both always appear.
        event.setDropItems(false);
        block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(Material.TORCHFLOWER, 1));
        block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(Material.TORCHFLOWER_SEEDS, 1));

        plugin.getLogger().info("[DEBUG] Items dropped successfully!");
        return true;
    }

    /**
     * Handle pitcher plant harvesting.
     * Returns {@code true} if perk was applied (vanilla drops cancelled and items
     * dropped manually), {@code false} if conditions are not met.
     *
     * <p>Pitcher plant is two blocks tall. Age data lives in the <em>top</em> block and
     * farmland must be checked under the <em>bottom</em> block. This method works
     * regardless of whether the player broke the top or bottom half.</p>
     */
    private boolean handlePitcherCrop(BlockBreakEvent event, Block block) {
        Block topBlock;
        Block bottomBlock;

        BlockData bd = block.getBlockData();
        if (!(bd instanceof Bisected bisected)) {
            return false; // PITCHER_CROP always implements Bisected; guard for safety
        }

        if (bisected.getHalf() == Bisected.Half.BOTTOM) {
            bottomBlock = block;
            Block above = block.getRelative(0, 1, 0);
            if (above.getType() != Material.PITCHER_CROP) {
                return false; // Incomplete plant
            }
            topBlock = above;
        } else {
            topBlock = block;
            Block below = block.getRelative(0, -1, 0);
            if (below.getType() != Material.PITCHER_CROP) {
                return false; // Incomplete plant
            }
            bottomBlock = below;
        }

        // Age is stored in the TOP block
        BlockData topData = topBlock.getBlockData();
        if (!(topData instanceof Ageable topAgeable)) {
            return false;
        }
        if (topAgeable.getAge() != topAgeable.getMaximumAge()) {
            return false;
        }

        // Farmland must be directly under the BOTTOM block
        if (bottomBlock.getRelative(0, -1, 0).getType() != Material.FARMLAND) {
            return false;
        }

        // Cancel vanilla drops – manually drop flower + seed so both always appear,
        // regardless of which half of the two-block plant the player broke.
        event.setDropItems(false);
        block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(Material.PITCHER_PLANT, 1));
        block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(Material.PITCHER_POD, 1));
        return true;
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
