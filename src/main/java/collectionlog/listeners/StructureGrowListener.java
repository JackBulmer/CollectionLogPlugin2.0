package collectionlog.listeners;

import collectionlog.CollectionLogPlugin;
import collectionlog.database.dao.BlockOwnershipDAO;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.StructureGrowEvent;

/**
 * Handles sapling growth into trees.
 * Removes the sapling's block ownership record so that Tree Capitator
 * can work on trees grown from player-placed saplings.
 */
public class StructureGrowListener implements Listener {

    private final BlockOwnershipDAO blockOwnershipDAO;

    public StructureGrowListener(CollectionLogPlugin plugin) {
        this.blockOwnershipDAO = plugin.getBlockOwnershipDAO();
    }

    @EventHandler
    public void onStructureGrow(StructureGrowEvent event) {
        for (BlockState blockState : event.getBlocks()) {
            Block block = blockState.getBlock();

            // If this location had a tracked sapling, remove it from tracking.
            // Do NOT track the new log — this allows Tree Capitator to work on
            // trees grown from player-placed saplings.
            if (blockOwnershipDAO.isTracked(block.getLocation())) {
                blockOwnershipDAO.removeBlock(block.getLocation());
            }
        }
    }
}
