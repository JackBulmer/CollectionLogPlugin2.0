package collectionlog.anticheat;

import collectionlog.database.AsyncTaskManager;
import collectionlog.database.dao.ContainerItemDAO;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Hybrid tracking system for items in naturally generated containers.
 * Uses in-memory cache for speed + database for persistence.
 */
public class ContainerTrackingManager {
    private final Plugin plugin;
    private final ContainerItemDAO dao;
    private final AsyncTaskManager asyncTaskManager;

    // Memory cache: "world:x:y:z" → (slot → TrackedItem)
    private final Map<String, Map<Integer, TrackedItem>> memoryCache = new HashMap<>();

    public ContainerTrackingManager(Plugin plugin, ContainerItemDAO dao, AsyncTaskManager asyncTaskManager) {
        this.plugin = plugin;
        this.dao = dao;
        this.asyncTaskManager = asyncTaskManager;
    }

    /**
     * Track an item placed in a container by a player
     */
    public void trackItem(Location containerLoc, int slot, ItemStack item, UUID playerUUID) {
        String locKey = locationToKey(containerLoc);

        // Track in memory cache (fast)
        synchronized (memoryCache) {
            memoryCache.putIfAbsent(locKey, new HashMap<>());
            memoryCache.get(locKey).put(slot, new TrackedItem(
                    item.getType(),
                    item.getAmount(),
                    playerUUID,
                    System.currentTimeMillis()
            ));
        }

        // Persist to database (async)
        asyncTaskManager.runAsync(() ->
                dao.trackItem(containerLoc, slot, item.getType(), item.getAmount(), playerUUID)
        );

        if (plugin.getConfig().getBoolean("plugin.debug", false)) {
            plugin.getLogger().info("Tracked " + item.getType() + " x" + item.getAmount()
                    + " in container at " + containerLoc.getBlockX() + ","
                    + containerLoc.getBlockY() + "," + containerLoc.getBlockZ()
                    + " slot " + slot + " (owner: " + playerUUID + ")");
        }
    }

    /**
     * Get the player who placed an item in a container.
     * Returns CompletableFuture for async support.
     */
    public CompletableFuture<UUID> getItemPlacer(Location containerLoc, int slot, ItemStack item) {
        String locKey = locationToKey(containerLoc);

        // Check memory cache first (fast path)
        synchronized (memoryCache) {
            Map<Integer, TrackedItem> slots = memoryCache.get(locKey);
            if (slots != null) {
                TrackedItem tracked = slots.get(slot);
                if (tracked != null && tracked.matches(item)) {
                    return CompletableFuture.completedFuture(tracked.playerUUID);
                }
            }
        }

        // Not in cache - check database (slow path)
        return asyncTaskManager.runAsync(() ->
                dao.getItemPlacer(containerLoc, slot, item.getType(), item.getAmount())
        );
    }

    /**
     * Remove tracking for a slot when item is taken
     */
    public void untrackSlot(Location containerLoc, int slot) {
        String locKey = locationToKey(containerLoc);

        // Remove from memory cache
        synchronized (memoryCache) {
            Map<Integer, TrackedItem> slots = memoryCache.get(locKey);
            if (slots != null) {
                slots.remove(slot);

                // Clean up empty containers
                if (slots.isEmpty()) {
                    memoryCache.remove(locKey);
                }
            }
        }

        // Remove from database (async)
        asyncTaskManager.runAsync(() -> dao.untrackSlot(containerLoc, slot));
    }

    /**
     * Remove all tracking for a container (when destroyed)
     */
    public void untrackContainer(Location containerLoc) {
        String locKey = locationToKey(containerLoc);

        // Remove from memory cache
        synchronized (memoryCache) {
            memoryCache.remove(locKey);
        }

        // Remove from database (async)
        asyncTaskManager.runAsync(() -> dao.untrackContainer(containerLoc));

        if (plugin.getConfig().getBoolean("plugin.debug", false)) {
            plugin.getLogger().info("Untracked container at "
                    + containerLoc.getBlockX() + ","
                    + containerLoc.getBlockY() + ","
                    + containerLoc.getBlockZ());
        }
    }

    /**
     * Get the number of tracked items in memory cache
     */
    public int getMemoryCacheSize() {
        synchronized (memoryCache) {
            int count = 0;
            for (Map<Integer, TrackedItem> slots : memoryCache.values()) {
                count += slots.size();
            }
            return count;
        }
    }

    /**
     * Clear all in-memory tracking (for plugin disable)
     */
    public void clearCache() {
        synchronized (memoryCache) {
            memoryCache.clear();
        }
    }

    private String locationToKey(Location loc) {
        return loc.getWorld().getName() + ":"
                + loc.getBlockX() + ":"
                + loc.getBlockY() + ":"
                + loc.getBlockZ();
    }

    /**
     * Internal class to track item details
     */
    private static class TrackedItem {
        final Material type;
        final int amount;
        final UUID playerUUID;
        final long placedAt;

        TrackedItem(Material type, int amount, UUID playerUUID, long placedAt) {
            this.type = type;
            this.amount = amount;
            this.playerUUID = playerUUID;
            this.placedAt = placedAt;
        }

        boolean matches(ItemStack item) {
            if (item == null) {
                return false;
            }
            return item.getType() == type && item.getAmount() == amount;
        }
    }
}
