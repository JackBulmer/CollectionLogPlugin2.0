package collectionlog.listeners;

import collectionlog.CollectionLogPlugin;
import collectionlog.perks.InfiniteLavaBucket;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.ItemStack;

public class InfiniteLavaBucketListener implements Listener {

    private final CollectionLogPlugin plugin;
    private final InfiniteLavaBucket infiniteLavaBucket;

    public InfiniteLavaBucketListener(CollectionLogPlugin plugin) {
        this.plugin = plugin;
        this.infiniteLavaBucket = plugin.getInfiniteLavaBucket();
    }

    /**
     * Prevents infinite lava bucket from being consumed when placing lava.
     * The bucket should remain as a lava bucket, not turn into an empty bucket.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        ItemStack bucket = event.getItemStack();

        if (!infiniteLavaBucket.isInfiniteLavaBucket(bucket)) {
            return;
        }

        Player player = event.getPlayer();

        // Allow the lava to be placed (event continues)
        // But restore the infinite lava bucket after vanilla converts it to an empty bucket
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            ItemStack offHand = player.getInventory().getItemInOffHand();

            // Check which hand had the bucket and restore it
            if (mainHand.getType() == Material.BUCKET) {
                player.getInventory().setItemInMainHand(infiniteLavaBucket.createItem());
            } else if (offHand.getType() == Material.BUCKET) {
                player.getInventory().setItemInOffHand(infiniteLavaBucket.createItem());
            }
        });
    }

    /**
     * Prevents infinite lava bucket from being consumed when used as fuel.
     * Sets burn time but keeps the bucket in the furnace.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFurnaceBurn(FurnaceBurnEvent event) {
        ItemStack fuel = event.getFuel();

        if (!infiniteLavaBucket.isInfiniteLavaBucket(fuel)) {
            return;
        }

        // Set the burn time to match a normal lava bucket (1000 items = 20000 ticks)
        event.setBurnTime(20000);

        // Restore the infinite lava bucket after vanilla consumes it
        Block furnaceBlock = event.getBlock();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (furnaceBlock.getState() instanceof org.bukkit.block.Furnace furnace) {
                restoreInFurnaceInventory(furnace.getInventory());
            } else if (furnaceBlock.getState() instanceof org.bukkit.block.BlastFurnace blastFurnace) {
                restoreInFurnaceInventory(blastFurnace.getInventory());
            } else if (furnaceBlock.getState() instanceof org.bukkit.block.Smoker smoker) {
                restoreInFurnaceInventory(smoker.getInventory());
            }
        });
    }

    private void restoreInFurnaceInventory(FurnaceInventory inventory) {
        ItemStack currentFuel = inventory.getFuel();
        if (currentFuel == null || currentFuel.getType() == Material.BUCKET || currentFuel.getType() == Material.AIR) {
            inventory.setFuel(infiniteLavaBucket.createItem());
        }
    }
}
