package collectionlog.listeners;

import collectionlog.utils.FortuneCropUtil;
import org.bukkit.GameMode;
import org.bukkit.Location;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Adds a Fortune bonus to the wheat item itself when a fully grown wheat crop is
 * harvested with a Fortune-enchanted hoe.
 *
 * <p>Vanilla Minecraft already applies Fortune to the bonus wheat seed drop, but the
 * wheat item always drops exactly 1 regardless of Fortune level. This listener keeps
 * that vanilla seed behaviour untouched and simply adds extra wheat on top of the
 * normal drops.</p>
 *
 * <p>Runs at {@code HIGH} priority with {@code ignoreCancelled = true} so it only
 * fires for ordinary single-crop breaks; when the Harvest &amp; Auto-Replant perk
 * handles the break itself (cancelling the event and managing drops manually), this
 * listener is skipped and {@link HarvestAndReplantListener} applies the same bonus.</p>
 */
public class FortuneWheatListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.WHEAT) {
            return;
        }

        Player player = event.getPlayer();

        // Creative mode never drops items; nothing to boost.
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        if (!event.isDropItems()) {
            return;
        }

        if (!isFullyGrown(block)) {
            return;
        }

        ItemStack tool = player.getInventory().getItemInMainHand();
        int fortuneLevel = FortuneCropUtil.getFortuneLevel(tool);
        if (fortuneLevel <= 0) {
            return;
        }

        int bonusWheat = FortuneCropUtil.rollBonusWheat(fortuneLevel);
        if (bonusWheat <= 0) {
            return;
        }

        // Take over drop handling for this break so we can add the bonus wheat
        // without disturbing the vanilla seed bonus already present in the drops.
        List<ItemStack> drops = new ArrayList<>(block.getDrops(tool, player));
        boolean merged = false;
        for (ItemStack drop : drops) {
            if (drop.getType() == Material.WHEAT) {
                drop.setAmount(drop.getAmount() + bonusWheat);
                merged = true;
                break;
            }
        }
        if (!merged) {
            drops.add(new ItemStack(Material.WHEAT, bonusWheat));
        }

        event.setDropItems(false);

        Location dropLoc = block.getLocation().add(0.5, 0.5, 0.5);
        for (ItemStack drop : drops) {
            block.getWorld().dropItemNaturally(dropLoc, drop);
        }
    }

    private boolean isFullyGrown(Block block) {
        BlockData blockData = block.getBlockData();
        if (!(blockData instanceof Ageable ageable)) {
            return false;
        }
        return ageable.getAge() == ageable.getMaximumAge();
    }
}
