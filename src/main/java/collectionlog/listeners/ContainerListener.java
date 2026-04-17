package collectionlog.listeners;

import collectionlog.CollectionLogPlugin;
import collectionlog.anticheat.ContainerTrackingManager;
import collectionlog.database.AsyncTaskManager;
import collectionlog.database.dao.BlockOwnershipDAO;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Tracks items placed in and taken from naturally generated containers.
 */
public class ContainerListener implements Listener {
    private final CollectionLogPlugin plugin;
    private final AsyncTaskManager asyncTaskManager;
    private final BlockOwnershipDAO blockOwnershipDAO;
    private final ContainerTrackingManager containerTrackingManager;

    public ContainerListener(CollectionLogPlugin plugin) {
        this.plugin = plugin;
        this.asyncTaskManager = plugin.getAsyncTaskManager();
        this.blockOwnershipDAO = plugin.getBlockOwnershipDAO();
        this.containerTrackingManager = plugin.getContainerTrackingManager();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory inventory = event.getInventory();
        if (!isContainer(inventory.getType()) || inventory.getLocation() == null) {
            return;
        }

        Location containerLocation = inventory.getLocation();
        Block containerBlock = containerLocation.getBlock();
        int slot = event.getSlot();
        int rawSlot = event.getRawSlot();
        int inventorySize = inventory.getSize();
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        ItemStack currentItem = cloneItemStackSafely(event.getCurrentItem());
        ItemStack cursor = cloneItemStackSafely(event.getCursor());

        asyncTaskManager.runAsync(() -> blockOwnershipDAO.getOwner(containerLocation))
                .thenAccept(containerPlacer -> {
                    if (containerPlacer != null) {
                        return;
                    }

                    asyncTaskManager.runSync(() -> processNaturalContainerClick(
                            containerBlock.getLocation(),
                            playerId,
                            playerName,
                            slot,
                            rawSlot,
                            inventorySize,
                            currentItem,
                            cursor
                    ));
                })
                .exceptionally(throwable -> {
                    plugin.getLogger().severe("Failed to process container click tracking: " + throwable.getMessage());
                    return null;
                });
    }

    private void processNaturalContainerClick(
            Location containerLocation,
            UUID playerId,
            String playerName,
            int slot,
            int rawSlot,
            int inventorySize,
            ItemStack currentItem,
            ItemStack cursor
    ) {
        // Player is taking an item OUT
        if (currentItem != null && !currentItem.getType().isAir() && rawSlot >= 0 && rawSlot < inventorySize) {
            containerTrackingManager.getItemPlacer(containerLocation, slot, currentItem)
                    .thenAccept(itemPlacer -> {
                        if (itemPlacer == null) {
                            if (plugin.getConfig().getBoolean("plugin.debug", false)) {
                                plugin.getLogger().info(playerName
                                        + " took natural loot from container (will get collection credit)");
                            }
                            return;
                        }

                        if (!itemPlacer.equals(playerId) && plugin.getConfig().getBoolean("plugin.debug", false)) {
                            plugin.getLogger().info(playerName
                                    + " took item from container placed by " + itemPlacer
                                    + " (will not get collection credit)");
                        }

                        containerTrackingManager.untrackSlot(containerLocation, slot);
                    })
                    .exceptionally(throwable -> {
                        plugin.getLogger().severe("Failed to resolve tracked container item owner: " + throwable.getMessage());
                        return null;
                    });
        }

        // Player is putting an item IN
        if (cursor != null && !cursor.getType().isAir()) {
            int targetSlot = rawSlot;
            if (targetSlot >= 0 && targetSlot < inventorySize) {
                containerTrackingManager.trackItem(containerLocation, targetSlot, cursor, playerId);

                if (plugin.getConfig().getBoolean("plugin.debug", false)) {
                    plugin.getLogger().info(playerName
                            + " placed " + cursor.getType() + " x" + cursor.getAmount()
                            + " in natural container (slot " + targetSlot + ")");
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onContainerBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!(block.getState() instanceof Container)) {
            return;
        }

        containerTrackingManager.untrackContainer(block.getLocation());
    }

    private boolean isContainer(InventoryType type) {
        return type == InventoryType.CHEST
                || type == InventoryType.BARREL
                || type == InventoryType.SHULKER_BOX
                || type == InventoryType.HOPPER
                || type == InventoryType.DROPPER
                || type == InventoryType.DISPENSER
                || type == InventoryType.FURNACE
                || type == InventoryType.BLAST_FURNACE
                || type == InventoryType.SMOKER;
    }

    private ItemStack cloneItemStackSafely(ItemStack item) {
        return item == null ? null : item.clone();
    }
}
