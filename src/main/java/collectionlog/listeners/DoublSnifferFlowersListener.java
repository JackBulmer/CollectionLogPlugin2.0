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

import java.util.Collection;
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

        // Only process sniffer flowers
        if (blockType != Material.TORCHFLOWER_CROP && blockType != Material.PITCHER_CROP) {
            return;
        }

        // Creative mode excluded (no drops)
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        UUID playerId = player.getUniqueId();

        // Check perk unlocked
        if (!perkManager.hasUnlocked(playerId, PERK_ID)) {
            return;
        }

        ItemStack bonusSeed;
        if (blockType == Material.TORCHFLOWER_CROP) {
            bonusSeed = handleTorchflower(block);
        } else {
            bonusSeed = handlePitcherCrop(block);
        }

        if (bonusSeed == null) {
            return;
        }

        // Collect vanilla drops, append the bonus seed, then cancel default drop behaviour
        // and re-drop everything so no items are lost and the collection is complete.
        Collection<ItemStack> drops = block.getDrops(player.getInventory().getItemInMainHand());
        drops.add(bonusSeed);
        event.setDropItems(false);
        for (ItemStack drop : drops) {
            block.getWorld().dropItemNaturally(block.getLocation(), drop);
        }
    }

    /**
     * Returns a bonus {@code TORCHFLOWER_SEEDS} item when the torchflower crop is
     * fully grown on farmland, or {@code null} if conditions are not met.
     */
    private ItemStack handleTorchflower(Block block) {
        BlockData blockData = block.getBlockData();
        if (!(blockData instanceof Ageable ageable)) {
            return null;
        }
        if (ageable.getAge() != ageable.getMaximumAge()) {
            return null;
        }
        if (block.getRelative(0, -1, 0).getType() != Material.FARMLAND) {
            return null;
        }
        return new ItemStack(Material.TORCHFLOWER_SEEDS, 1);
    }

    /**
     * Returns a bonus {@code PITCHER_POD} item when the pitcher plant is fully grown on
     * farmland, or {@code null} if conditions are not met.
     *
     * <p>Pitcher plant is two blocks tall. Age data lives in the <em>top</em> block and
     * farmland must be checked under the <em>bottom</em> block. This method works
     * regardless of whether the player broke the top or bottom half.</p>
     */
    private ItemStack handlePitcherCrop(Block block) {
        Block topBlock;
        Block bottomBlock;

        BlockData bd = block.getBlockData();
        if (!(bd instanceof Bisected bisected)) {
            return null; // PITCHER_CROP always implements Bisected; guard for safety
        }

        if (bisected.getHalf() == Bisected.Half.BOTTOM) {
            bottomBlock = block;
            Block above = block.getRelative(0, 1, 0);
            if (above.getType() != Material.PITCHER_CROP) {
                return null; // Incomplete plant
            }
            topBlock = above;
        } else {
            topBlock = block;
            Block below = block.getRelative(0, -1, 0);
            if (below.getType() != Material.PITCHER_CROP) {
                return null; // Incomplete plant
            }
            bottomBlock = below;
        }

        // Age is stored in the TOP block
        BlockData topData = topBlock.getBlockData();
        if (!(topData instanceof Ageable topAgeable)) {
            return null;
        }
        if (topAgeable.getAge() != topAgeable.getMaximumAge()) {
            return null;
        }

        // Farmland must be directly under the BOTTOM block
        if (bottomBlock.getRelative(0, -1, 0).getType() != Material.FARMLAND) {
            return null;
        }

        return new ItemStack(Material.PITCHER_POD, 1);
    }
}
