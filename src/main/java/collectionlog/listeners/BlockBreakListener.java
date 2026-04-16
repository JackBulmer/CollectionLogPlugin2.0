package collectionlog.listeners;

import collectionlog.CollectionLogPlugin;
import collectionlog.database.AsyncTaskManager;
import collectionlog.database.dao.BlockOwnershipDAO;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Handles block break events for ownership checking and item tracking.
 */
public class BlockBreakListener implements Listener {
    private final CollectionLogPlugin plugin;
    private final BlockOwnershipDAO dao;
    private final AsyncTaskManager asyncTaskManager;

    public BlockBreakListener(CollectionLogPlugin plugin) {
        this.plugin = plugin;
        this.dao = plugin.getBlockOwnershipDAO();
        this.asyncTaskManager = plugin.getAsyncTaskManager();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Location location = event.getBlock().getLocation();
        Player player = event.getPlayer();
        String playerName = player.getName();

        // Check ownership asynchronously
        asyncTaskManager.runAsync(() -> dao.getOwner(location))
                .thenAccept(owner -> {
                    if (owner != null) {
                        // Block was placed by a player
                        if (plugin.getConfig().getBoolean("plugin.debug", false)) {
                            plugin.getLogger().info("Player " + playerName + " broke block placed by " + owner);
                        }

                        // Track dropped items as belonging to the placer
                        // Must run sync to access world entities
                        asyncTaskManager.runSync(() -> {
                            try {
                                // Small delay to let items spawn
                                new BukkitRunnable() {
                                    @Override
                                    public void run() {
                                        Location searchLocation = location.clone().add(0.5, 0.5, 0.5);

                                        searchLocation.getWorld().getNearbyEntities(
                                                searchLocation,
                                                1.5, 1.5, 1.5
                                        ).forEach(entity -> {
                                            if (entity instanceof Item item) {
                                                plugin.getItemTrackingManager().trackItem(item, owner);

                                                if (plugin.getConfig().getBoolean("plugin.debug", false)) {
                                                    plugin.getLogger().info("Tracking drop from placed block: "
                                                            + item.getItemStack().getType() + " (owner: " + owner + ")");
                                                }
                                            }
                                        });
                                    }
                                }.runTaskLater(plugin, 1L);
                            } catch (Exception e) {
                                plugin.getLogger().severe("Failed to track dropped items from broken placed block: " + e.getMessage());
                            }
                        });

                        // Remove from tracking (async)
                        asyncTaskManager.runAsync(() -> dao.removeBlock(location))
                                .exceptionally(throwable -> {
                                    plugin.getLogger().severe("Failed to remove broken placed block from tracking: " + throwable.getMessage());
                                    return null;
                                });
                    } else if (plugin.getConfig().getBoolean("plugin.debug", false)) {
                        // Natural block - no tracking needed
                        plugin.getLogger().info("Player " + playerName + " broke natural block (items will give credit)");
                    }
                })
                .exceptionally(throwable -> {
                    plugin.getLogger().severe("Failed to process block break ownership tracking: " + throwable.getMessage());
                    return null;
                });
    }
}
