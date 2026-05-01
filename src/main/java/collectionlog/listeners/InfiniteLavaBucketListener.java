package collectionlog.listeners;

import collectionlog.CollectionLogPlugin;
import collectionlog.perks.InfiniteLavaBucket;
import org.bukkit.Material;
import org.bukkit.Sound;
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
     * Cancels the event and manually places lava to keep the bucket.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        ItemStack bucket = event.getItemStack();

        // Check if this is an infinite lava bucket
        if (!infiniteLavaBucket.isInfiniteLavaBucket(bucket)) {
            return;
        }

        // Cancel the event to prevent vanilla bucket consumption
        event.setCancelled(true);

        // Manually place the lava block
        Block clickedBlock = event.getBlockClicked();
        Block placementBlock = clickedBlock.getRelative(event.getBlockFace());

        // Check if we can place lava here (not solid, not protected, etc.)
        if (placementBlock.getType() != Material.AIR &&
                placementBlock.getType() != Material.CAVE_AIR &&
                placementBlock.getType() != Material.VOID_AIR &&
                !placementBlock.isLiquid()) {
            // Cannot place lava here
            return;
        }

        // Place the lava
        if (!placementBlock.canPlace(Material.LAVA.createBlockData())) {
            return;
        }
        placementBlock.setType(Material.LAVA);

        // Play sound effect (same as vanilla bucket emptying)
        player.playSound(placementBlock.getLocation(), Sound.ITEM_BUCKET_EMPTY_LAVA, 1.0f, 1.0f);

        // Infinite lava bucket stays in hand - no item changes needed!
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
