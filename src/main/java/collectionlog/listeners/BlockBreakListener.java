package collectionlog.listeners;

import collectionlog.CollectionLogPlugin;
import collectionlog.database.AsyncTaskManager;
import collectionlog.database.dao.BlockOwnershipDAO;
import collectionlog.managers.ProgressTrackingManager;
import collectionlog.utils.CollectionMatcher;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Handles block break events for ownership checking and item tracking.
 */
public class BlockBreakListener implements Listener {
    private static final double BLOCK_CENTER_OFFSET = 0.5;
    private static final double ITEM_SEARCH_RADIUS = 1.5;

    private final CollectionLogPlugin plugin;
    private final BlockOwnershipDAO dao;
    private final AsyncTaskManager asyncTaskManager;
    private final CollectionMatcher collectionMatcher;
    private final ProgressTrackingManager progressTrackingManager;

    public BlockBreakListener(CollectionLogPlugin plugin) {
        this.plugin = plugin;
        this.dao = plugin.getBlockOwnershipDAO();
        this.asyncTaskManager = plugin.getAsyncTaskManager();
        this.collectionMatcher = plugin.getCollectionMatcher();
        this.progressTrackingManager = plugin.getProgressTrackingManager();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Location location = event.getBlock().getLocation();
        Material brokenType = event.getBlock().getType();
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
                                        Location searchLocation = location.clone().add(
                                                BLOCK_CENTER_OFFSET,
                                                BLOCK_CENTER_OFFSET,
                                                BLOCK_CENTER_OFFSET
                                        );

                                        searchLocation.getWorld().getNearbyEntities(
                                                searchLocation,
                                                ITEM_SEARCH_RADIUS,
                                                ITEM_SEARCH_RADIUS,
                                                ITEM_SEARCH_RADIUS
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
                    } else {
                        if (plugin.getConfig().getBoolean("plugin.debug", false)) {
                            plugin.getLogger().info("Player " + playerName + " broke natural block (items will give credit)");
                        }
                        asyncTaskManager.runSync(() ->
                                trackCollectionProgress(player, new ItemStack(brokenType)));
                    }
                })
                .exceptionally(throwable -> {
                    plugin.getLogger().severe("Failed to process block break ownership tracking: " + throwable.getMessage());
                    return null;
                });
    }

    private void trackCollectionProgress(Player player, ItemStack item) {
        if (collectionMatcher == null || progressTrackingManager == null) {
            return;
        }

        for (CollectionMatcher.CollectionMatch match : collectionMatcher.findMatches(item)) {
            progressTrackingManager.trackItemCollection(player, match.collection(), match.signature())
                    .exceptionally(throwable -> {
                        plugin.getLogger().severe("Failed to track mined item progress: " + throwable.getMessage());
                        return null;
                    });
        }
    }
}
