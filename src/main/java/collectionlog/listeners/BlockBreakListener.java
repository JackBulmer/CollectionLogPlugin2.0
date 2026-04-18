package collectionlog.listeners;

import collectionlog.CollectionLogPlugin;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * Handles block break events for ownership tracking.
 */
public class BlockBreakListener implements Listener {
    private final CollectionLogPlugin plugin;

    public BlockBreakListener(CollectionLogPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Location location = event.getBlock().getLocation();
        plugin.getBlockOwnershipDAO().removeBlock(location);
    }
}
