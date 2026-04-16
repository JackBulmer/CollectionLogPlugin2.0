package collectionlog.database.dao;

import collectionlog.database.DatabaseManager;
import collectionlog.models.BlockLocation;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Data Access Object for block_ownership table.
 * Handles all database operations for tracking player-placed blocks.
 */
public class BlockOwnershipDAO {
    private final Plugin plugin;
    private final DatabaseManager databaseManager;

    public BlockOwnershipDAO(Plugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    /**
     * Track a block placed by a player
     *
     * @param location Block location
     * @param ownerUUID Player UUID who placed the block
     */
    public void trackBlock(Location location, UUID ownerUUID) {
        BlockLocation blockLoc = new BlockLocation(location);
        trackBlock(blockLoc, ownerUUID);
    }

    /**
     * Track a block placed by a player (using BlockLocation)
     *
     * @param location Block location
     * @param ownerUUID Player UUID who placed the block
     */
    public void trackBlock(BlockLocation location, UUID ownerUUID) {
        String sql = "INSERT OR REPLACE INTO block_ownership (world, x, y, z, owner_uuid, placed_at) VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, location.getWorldName());
            stmt.setInt(2, location.getX());
            stmt.setInt(3, location.getY());
            stmt.setInt(4, location.getZ());
            stmt.setString(5, ownerUUID.toString());
            stmt.setLong(6, System.currentTimeMillis());

            stmt.executeUpdate();

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to track block at " + location, e);
        }
    }

    /**
     * Get the owner of a block
     *
     * @param location Block location
     * @return Player UUID who placed the block, or null if not tracked
     */
    public UUID getOwner(Location location) {
        BlockLocation blockLoc = new BlockLocation(location);
        return getOwner(blockLoc);
    }

    /**
     * Get the owner of a block (using BlockLocation)
     *
     * @param location Block location
     * @return Player UUID who placed the block, or null if not tracked
     */
    public UUID getOwner(BlockLocation location) {
        String sql = "SELECT owner_uuid FROM block_ownership WHERE world = ? AND x = ? AND y = ? AND z = ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, location.getWorldName());
            stmt.setInt(2, location.getX());
            stmt.setInt(3, location.getY());
            stmt.setInt(4, location.getZ());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String uuidStr = rs.getString("owner_uuid");
                    return UUID.fromString(uuidStr);
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get owner for block at " + location, e);
        }

        return null;
    }

    /**
     * Check if a block is tracked (placed by a player)
     *
     * @param location Block location
     * @return true if block is tracked, false otherwise
     */
    public boolean isTracked(Location location) {
        return getOwner(location) != null;
    }

    /**
     * Remove a block from tracking (when broken/destroyed)
     *
     * @param location Block location
     * @return true if block was removed, false if it wasn't tracked
     */
    public boolean removeBlock(Location location) {
        BlockLocation blockLoc = new BlockLocation(location);
        return removeBlock(blockLoc);
    }

    /**
     * Remove a block from tracking (using BlockLocation)
     *
     * @param location Block location
     * @return true if block was removed, false if it wasn't tracked
     */
    public boolean removeBlock(BlockLocation location) {
        String sql = "DELETE FROM block_ownership WHERE world = ? AND x = ? AND y = ? AND z = ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, location.getWorldName());
            stmt.setInt(2, location.getX());
            stmt.setInt(3, location.getY());
            stmt.setInt(4, location.getZ());

            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to remove block at " + location, e);
            return false;
        }
    }

    /**
     * Move a block's ownership (for piston movements)
     *
     * @param from Original location
     * @param to New location
     * @return true if block was moved, false if it wasn't tracked
     */
    public boolean moveBlock(Location from, Location to) {
        BlockLocation fromLoc = new BlockLocation(from);
        BlockLocation toLoc = new BlockLocation(to);
        return moveBlock(fromLoc, toLoc);
    }

    /**
     * Move a block's ownership (using BlockLocation)
     *
     * @param from Original location
     * @param to New location
     * @return true if block was moved, false if it wasn't tracked
     */
    public boolean moveBlock(BlockLocation from, BlockLocation to) {
        UUID owner = getOwner(from);
        if (owner == null) {
            return false;
        }

        removeBlock(from);
        trackBlock(to, owner);

        return true;
    }

    /**
     * Get the total number of tracked blocks
     *
     * @return Number of tracked blocks in database
     */
    public long getTrackedBlockCount() {
        String sql = "SELECT COUNT(*) as count FROM block_ownership";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                return rs.getLong("count");
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get tracked block count", e);
        }

        return 0;
    }

    /**
     * Clean up old block ownership records (optional maintenance)
     * Removes blocks placed more than X days ago
     *
     * @param daysOld Number of days to keep
     * @return Number of records deleted
     */
    public int cleanupOldBlocks(int daysOld) {
        long cutoffTime = System.currentTimeMillis() - (daysOld * 24L * 60L * 60L * 1000L);
        String sql = "DELETE FROM block_ownership WHERE placed_at < ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, cutoffTime);
            int deleted = stmt.executeUpdate();

            if (deleted > 0) {
                plugin.getLogger().info("Cleaned up " + deleted + " old block ownership records");
            }

            return deleted;

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to cleanup old blocks", e);
            return 0;
        }
    }
}
