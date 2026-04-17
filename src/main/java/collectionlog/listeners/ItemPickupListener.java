package collectionlog.listeners;

import collectionlog.CollectionLogPlugin;
import collectionlog.anticheat.ItemTrackingManager;
import collectionlog.managers.ProgressTrackingManager;
import collectionlog.utils.CollectionMatcher;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Tracks item pickups to prevent collection credit theft.
 * Players CAN pick up items, but won't get credit if they didn't earn them.
 */
public class ItemPickupListener implements Listener {
    private final CollectionLogPlugin plugin;
    private final ItemTrackingManager itemTrackingManager;
    private final CollectionMatcher collectionMatcher;
    private final ProgressTrackingManager progressTrackingManager;

    public ItemPickupListener(CollectionLogPlugin plugin) {
        this.plugin = plugin;
        this.itemTrackingManager = plugin.getItemTrackingManager();
        this.collectionMatcher = plugin.getCollectionMatcher();
        this.progressTrackingManager = plugin.getProgressTrackingManager();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player picker)) {
            return;
        }

        UUID dropper = itemTrackingManager.getDropper(event.getItem());
        boolean shouldTrackCollectionCredit = false;
        if (dropper == null) {
            shouldTrackCollectionCredit = true;
            if (plugin.getConfig().getBoolean("plugin.debug", false)) {
                plugin.getLogger().info(picker.getName() + " picked up natural item (will get collection credit)");
            }
        } else if (!dropper.equals(picker.getUniqueId())) {
            // Pickup is allowed; only credit is denied in later collection-processing phases.
            if (plugin.getConfig().getBoolean("plugin.debug", false)) {
                plugin.getLogger().info(picker.getName() + " picked up item from " + dropper
                        + " (will not get collection credit)");
            }
        } else {
            shouldTrackCollectionCredit = true;
            if (plugin.getConfig().getBoolean("plugin.debug", false)) {
                plugin.getLogger().info(picker.getName() + " picked up their own item (will get collection credit)");
            }
        }

        if (shouldTrackCollectionCredit) {
            trackCollectionProgress(picker, event.getItem().getItemStack());
        }

        // Clean up tracking immediately after pickup
        itemTrackingManager.untrack(event.getItem());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemDespawn(ItemDespawnEvent event) {
        if (!itemTrackingManager.isTracked(event.getEntity())) {
            return;
        }

        itemTrackingManager.untrack(event.getEntity());

        if (plugin.getConfig().getBoolean("plugin.debug", false)) {
            plugin.getLogger().info("Item despawned, removed from tracking");
        }
    }

    private void trackCollectionProgress(Player player, ItemStack item) {
        if (collectionMatcher == null || progressTrackingManager == null) {
            return;
        }

        for (CollectionMatcher.CollectionMatch match : collectionMatcher.findMatches(item)) {
            progressTrackingManager.trackItemCollection(player, match.collection(), match.signature())
                    .exceptionally(throwable -> {
                        plugin.getLogger().severe("Failed to track picked-up item progress: " + throwable.getMessage());
                        return null;
                    });
        }
    }
}
