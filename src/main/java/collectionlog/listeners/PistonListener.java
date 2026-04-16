package collectionlog.listeners;

import collectionlog.CollectionLogPlugin;
import collectionlog.database.AsyncTaskManager;
import collectionlog.database.dao.BlockOwnershipDAO;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracks blocks moved by pistons to maintain ownership.
 * This prevents the exploit where players use pistons to move placed blocks.
 */
public class PistonListener implements Listener {
    private final CollectionLogPlugin plugin;
    private final BlockOwnershipDAO dao;
    private final AsyncTaskManager asyncTaskManager;

    public PistonListener(CollectionLogPlugin plugin) {
        this.plugin = plugin;
        this.dao = plugin.getBlockOwnershipDAO();
        this.asyncTaskManager = plugin.getAsyncTaskManager();
    }

    private record BlockMove(Location from, Location to) {
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        List<Block> blocks = event.getBlocks();
        BlockFace direction = event.getDirection();

        if (blocks.isEmpty()) {
            return;
        }

        List<BlockMove> moves = new ArrayList<>(blocks.size());
        for (Block block : blocks) {
            Location from = block.getLocation();
            Location to = from.clone().add(direction.getModX(), direction.getModY(), direction.getModZ());
            moves.add(new BlockMove(from, to));
        }

        // Move blocks in reverse order (farthest first) to avoid conflicts
        asyncTaskManager.runAsync(() -> {
            for (int i = moves.size() - 1; i >= 0; i--) {
                BlockMove move = moves.get(i);
                Location from = move.from();
                Location to = move.to();

                dao.moveBlock(from, to);

                if (plugin.getConfig().getBoolean("plugin.debug", false)) {
                    plugin.getLogger().info("Moved block from "
                            + from.getBlockX() + "," + from.getBlockY() + "," + from.getBlockZ()
                            + " to " + to.getBlockX() + "," + to.getBlockY() + "," + to.getBlockZ());
                }
            }
        }).exceptionally(throwable -> {
            plugin.getLogger().severe("Failed to track piston extension block movement: " + throwable.getMessage());
            return null;
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        List<Block> blocks = event.getBlocks();
        BlockFace direction = event.getDirection();

        if (blocks.isEmpty()) {
            return;
        }

        List<BlockMove> moves = new ArrayList<>(blocks.size());
        for (Block block : blocks) {
            Location from = block.getLocation();
            Location to = from.clone().add(direction.getModX(), direction.getModY(), direction.getModZ());
            moves.add(new BlockMove(from, to));
        }

        // Move blocks in order (closest first) to avoid conflicts
        asyncTaskManager.runAsync(() -> {
            for (BlockMove move : moves) {
                Location from = move.from();
                Location to = move.to();

                dao.moveBlock(from, to);

                if (plugin.getConfig().getBoolean("plugin.debug", false)) {
                    plugin.getLogger().info("Retracted block from "
                            + from.getBlockX() + "," + from.getBlockY() + "," + from.getBlockZ()
                            + " to " + to.getBlockX() + "," + to.getBlockY() + "," + to.getBlockZ());
                }
            }
        }).exceptionally(throwable -> {
            plugin.getLogger().severe("Failed to track piston retraction block movement: " + throwable.getMessage());
            return null;
        });
    }
}
