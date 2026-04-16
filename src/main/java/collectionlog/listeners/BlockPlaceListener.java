package collectionlog.listeners;

import collectionlog.CollectionLogPlugin;
import collectionlog.database.AsyncTaskManager;
import collectionlog.database.dao.BlockOwnershipDAO;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.UUID;

/**
 * Tracks blocks placed by players for anti-cheat purposes.
 */
public class BlockPlaceListener implements Listener {
    private final CollectionLogPlugin plugin;
    private final BlockOwnershipDAO dao;
    private final AsyncTaskManager asyncTaskManager;

    public BlockPlaceListener(CollectionLogPlugin plugin) {
        this.plugin = plugin;
        this.dao = plugin.getBlockOwnershipDAO();
        this.asyncTaskManager = plugin.getAsyncTaskManager();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Location location = event.getBlock().getLocation();
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        String playerName = player.getName();

        // Track block ownership asynchronously (no lag!)
        asyncTaskManager.runAsync(() -> {
            dao.trackBlock(location, playerUUID);

            if (plugin.getConfig().getBoolean("plugin.debug", false)) {
                plugin.getLogger().info("Tracked block at "
                        + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ()
                        + " for player " + playerName);
            }
        }).exceptionally(throwable -> {
            plugin.getLogger().severe("Failed to track placed block: " + throwable.getMessage());
            return null;
        });
    }
}
