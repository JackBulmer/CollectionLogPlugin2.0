package collectionlog.listeners;

import collectionlog.CollectionLogPlugin;
import collectionlog.utils.FortuneCropUtil;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;

/**
 * Applies a Fortune-enchantment bonus to WHEAT drops.
 *
 * <p>Vanilla Fortune only increases the number of wheat seeds dropped from a fully
 * grown wheat crop; the wheat item itself always drops exactly 1 regardless of
 * Fortune level. This listener adds extra wheat items on top of the normal drops
 * when the breaking tool has Fortune, while leaving the vanilla seed bonus
 * (already calculated by {@link Block#getDrops(ItemStack, org.bukkit.entity.Entity)})
 * completely untouched.</p>
 *
 * <p>Runs at HIGH priority so it fires after {@link HarvestAndReplantListener}
 * (NORMAL), which cancels the event when it takes over handling the crop via its
 * own 5x5 harvest – the same Fortune bonus is applied inline there instead.</p>
 */
public class FortuneWheatListener implements Listener {

    public FortuneWheatListener(CollectionLogPlugin plugin) {
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.WHEAT) {
            return;
        }

        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        BlockData blockData = block.getBlockData();
        if (!(blockData instanceof Ageable ageable) || ageable.getAge() != ageable.getMaximumAge()) {
            return;
        }

        ItemStack tool = player.getInventory().getItemInMainHand();
        int bonusWheat = FortuneCropUtil.rollFortuneBonusWheat(tool);
        if (bonusWheat <= 0) {
            return;
        }

        // Preserve the vanilla drops (wheat + Fortune-boosted seeds, computed by the
        // loot table via getDrops) and add the extra wheat on top, rather than
        // recomputing or duplicating any part of the vanilla drop list.
        event.setDropItems(false);
        Collection<ItemStack> drops = block.getDrops(tool, player);
        for (ItemStack drop : drops) {
            block.getWorld().dropItemNaturally(block.getLocation(), drop);
        }
        block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(Material.WHEAT, bonusWheat));
    }
}
