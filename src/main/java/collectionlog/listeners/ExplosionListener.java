package collectionlog.listeners;

import collectionlog.CollectionLogPlugin;
import collectionlog.database.AsyncTaskManager;
import collectionlog.database.dao.BlockOwnershipDAO;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles explosions to remove destroyed blocks from tracking.
 */
public class ExplosionListener implements Listener {
    private final CollectionLogPlugin plugin;
    private final BlockOwnershipDAO dao;
    private final AsyncTaskManager asyncTaskManager;

    public ExplosionListener(CollectionLogPlugin plugin) {
        this.plugin = plugin;
        this.dao = plugin.getBlockOwnershipDAO();
        this.asyncTaskManager = plugin.getAsyncTaskManager();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        List<Block> blocks = event.blockList();

        if (blocks.isEmpty()) {
            return;
        }

        List<Location> locations = new ArrayList<>(blocks.size());
        for (Block block : blocks) {
            locations.add(block.getLocation());
        }

        // Remove all destroyed blocks from tracking
        asyncTaskManager.runAsync(() -> {
            int removed = 0;
            for (Location location : locations) {
                if (dao.removeBlock(location)) {
                    removed++;
                }
            }

            if (removed > 0 && plugin.getConfig().getBoolean("plugin.debug", false)) {
                plugin.getLogger().info("Removed " + removed + " blocks from tracking (entity explosion)");
            }
        }).exceptionally(throwable -> {
            plugin.getLogger().severe("Failed to process entity explosion tracking cleanup: " + throwable.getMessage());
            return null;
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        List<Block> blocks = event.blockList();

        if (blocks.isEmpty()) {
            return;
        }

        List<Location> locations = new ArrayList<>(blocks.size());
        for (Block block : blocks) {
            locations.add(block.getLocation());
        }

        // Remove all destroyed blocks from tracking
        asyncTaskManager.runAsync(() -> {
            int removed = 0;
            for (Location location : locations) {
                if (dao.removeBlock(location)) {
                    removed++;
                }
            }

            if (removed > 0 && plugin.getConfig().getBoolean("plugin.debug", false)) {
                plugin.getLogger().info("Removed " + removed + " blocks from tracking (block explosion)");
            }
        }).exceptionally(throwable -> {
            plugin.getLogger().severe("Failed to process block explosion tracking cleanup: " + throwable.getMessage());
            return null;
        });
    }
}
