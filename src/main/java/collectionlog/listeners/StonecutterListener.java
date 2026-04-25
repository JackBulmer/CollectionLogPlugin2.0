package collectionlog.listeners;

import collectionlog.CollectionLogPlugin;
import collectionlog.managers.ProgressTrackingManager;
import collectionlog.utils.CollectionMatcher;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.StonecutterInventory;

/**
 * Tracks item collection when players craft items via stonecutter.
 */
public class StonecutterListener implements Listener {
    private final CollectionLogPlugin plugin;
    private final CollectionMatcher collectionMatcher;
    private final ProgressTrackingManager progressTrackingManager;

    public StonecutterListener(CollectionLogPlugin plugin) {
        this.plugin = plugin;
        this.collectionMatcher = plugin.getCollectionMatcher();
        this.progressTrackingManager = plugin.getProgressTrackingManager();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStonecutterClick(InventoryClickEvent event) {
        // Only stonecutter inventory
        if (!(event.getInventory() instanceof StonecutterInventory)) {
            return;
        }

        // Only player clicks
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // Stonecutter result slot is slot 1 (raw slot 1)
        if (event.getRawSlot() != 1) {
            return;
        }

        // Only track on pickup actions (when player takes the item)
        InventoryAction action = event.getAction();
        if (action != InventoryAction.PICKUP_ALL
            && action != InventoryAction.PICKUP_HALF
            && action != InventoryAction.PICKUP_ONE
            && action != InventoryAction.PICKUP_SOME
            && action != InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            return;
        }

        // Check both current item and cursor item
        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType().isAir()) {
            result = event.getCursor();
        }

        if (result == null || result.getType().isAir()) {
            return;
        }

        if (collectionMatcher == null || progressTrackingManager == null) {
            return;
        }

        for (CollectionMatcher.CollectionMatch match : collectionMatcher.findMatches(result, null)) {
            progressTrackingManager.trackItemCollection(player, match.collection(), match.signature())
                    .exceptionally(throwable -> {
                        plugin.getLogger().severe("Failed to track stonecutter craft: " + throwable.getMessage());
                        return null;
                    });
        }
    }
}
