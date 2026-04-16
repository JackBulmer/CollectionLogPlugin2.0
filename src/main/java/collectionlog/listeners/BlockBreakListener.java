package collectionlog.listeners;

import collectionlog.CollectionLogPlugin;
import collectionlog.database.AsyncTaskManager;
import collectionlog.database.dao.BlockOwnershipDAO;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.UUID;

/**
 * Handles block break events for ownership checking and cleanup.
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

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Location location = event.getBlock().getLocation();
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        String playerName = player.getName();

        // Check ownership and cleanup asynchronously
        asyncTaskManager.runAsync(() -> {
            UUID owner = dao.getOwner(location);
            if (owner != null) {
                // Block was placed by a player
                if (!owner.equals(playerUUID) && plugin.getConfig().getBoolean("plugin.debug", false)) {
                    plugin.getLogger().info("Player " + playerName + " broke block placed by " + owner);
                    // TODO: In future, deny collection credit here
                }

                dao.removeBlock(location);
            }
        })
            .exceptionally(throwable -> {
                plugin.getLogger().severe("Failed to process block break ownership tracking: " + throwable.getMessage());
                return null;
            });
    }
}
