package collectionlog.listeners;

import collectionlog.CollectionLogPlugin;
import collectionlog.perks.InfiniteLavaBucket;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
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
     * Intercepts right-click interactions with infinite lava bucket at the earliest priority.
     * This runs BEFORE PlayerBucketEmptyEvent is even fired.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Only handle right-click on blocks
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        // Ignore off-hand to prevent double processing
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        // Check if this is an infinite lava bucket
        if (!infiniteLavaBucket.isInfiniteLavaBucket(item)) {
            return;
        }

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }

        // Don't place lava if clicking on an interactable block (unless sneaking)
        if (!player.isSneaking() && clickedBlock.isInteractable()) {
            return;
        }

        BlockFace face = event.getBlockFace();
        Block targetBlock = clickedBlock.getRelative(face);

        // Check if we can place lava at this location
        if (!canPlaceLava(targetBlock)) {
            return;
        }

        // Cancel the event completely - this prevents PlayerBucketEmptyEvent from firing
        event.setCancelled(true);

        // Place the lava block
        targetBlock.setType(Material.LAVA);

        // Play sound effect
        player.playSound(targetBlock.getLocation(), Sound.ITEM_BUCKET_EMPTY_LAVA, 1.0f, 1.0f);

        // Swing arm animation for visual feedback
        player.swingMainHand();

        // Item stays as infinite lava bucket - no changes needed!
    }

    /**
     * Checks if lava can be placed at the target location.
     */
    private boolean canPlaceLava(Block block) {
        Material type = block.getType();

        // Can replace air, water, and other liquids
        if (type == Material.AIR ||
            type == Material.CAVE_AIR ||
            type == Material.VOID_AIR ||
            type == Material.WATER ||
            type == Material.LAVA) {
            return true;
        }

        // Can replace naturally replaceable blocks (tall grass, flowers, snow layers, etc.)
        return block.isReplaceable();
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
        Bukkit.getScheduler().runTask(plugin, () -> {
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
