package collectionlog.anticheat;

import org.bukkit.entity.Item;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks items dropped by players without using PDC.
 * This prevents the item stacking bug caused by PDC tags.
 *
 * Items are tracked by entity UUID and automatically cleaned up after 5 seconds.
 */
public class ItemTrackingManager {
    private final Plugin plugin;
    private final Map<UUID, UUID> droppedItems = new ConcurrentHashMap<>();

    public ItemTrackingManager(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Track an item dropped by a player
     *
     * @param itemEntity The dropped item entity
     * @param playerUUID The player who dropped it
     */
    public void trackItem(Item itemEntity, UUID playerUUID) {
        UUID itemUUID = itemEntity.getUniqueId();
        droppedItems.put(itemUUID, playerUUID);

        // Auto-cleanup after 5 seconds
        new BukkitRunnable() {
            @Override
            public void run() {
                droppedItems.remove(itemUUID);
            }
        }.runTaskLater(plugin, 100L); // 5 seconds (100 ticks)
    }

    /**
     * Get the player who dropped an item
     *
     * @param itemEntity The item entity
     * @return Player UUID who dropped it, or null if not tracked
     */
    public UUID getDropper(Item itemEntity) {
        return droppedItems.get(itemEntity.getUniqueId());
    }

    /**
     * Check if an item was dropped by a player
     *
     * @param itemEntity The item entity
     * @return true if tracked, false otherwise
     */
    public boolean isTracked(Item itemEntity) {
        return droppedItems.containsKey(itemEntity.getUniqueId());
    }

    /**
     * Remove an item from tracking
     *
     * @param itemEntity The item entity
     */
    public void untrack(Item itemEntity) {
        droppedItems.remove(itemEntity.getUniqueId());
    }

    /**
     * Get the number of tracked items
     *
     * @return Number of items currently tracked
     */
    public int getTrackedCount() {
        return droppedItems.size();
    }

    /**
     * Clear all tracked items (for plugin disable)
     */
    public void clearAll() {
        droppedItems.clear();
    }
}
