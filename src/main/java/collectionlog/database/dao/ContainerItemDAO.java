package collectionlog.database.dao;

import collectionlog.database.DatabaseManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Data Access Object for container_items table.
 * Tracks items placed by players in naturally generated containers.
 */
public class ContainerItemDAO {
    private final Plugin plugin;
    private final DatabaseManager databaseManager;

    public ContainerItemDAO(Plugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    /**
     * Track an item placed in a container
     */
    public void trackItem(Location containerLoc, int slot, Material itemType, int amount, UUID placerUUID) {
        String sql = "INSERT OR REPLACE INTO container_items "
                + "(world, x, y, z, slot, item_type, item_amount, placer_uuid, placed_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, containerLoc.getWorld().getName());
            stmt.setInt(2, containerLoc.getBlockX());
            stmt.setInt(3, containerLoc.getBlockY());
            stmt.setInt(4, containerLoc.getBlockZ());
            stmt.setInt(5, slot);
            stmt.setString(6, itemType.name());
            stmt.setInt(7, amount);
            stmt.setString(8, placerUUID.toString());
            stmt.setLong(9, System.currentTimeMillis());

            stmt.executeUpdate();

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to track container item", e);
        }
    }

    /**
     * Check if an item in a container was player-placed
     */
    public UUID getItemPlacer(Location containerLoc, int slot, Material itemType, int amount) {
        String sql = "SELECT placer_uuid FROM container_items "
                + "WHERE world = ? AND x = ? AND y = ? AND z = ? AND slot = ? "
                + "AND item_type = ? AND item_amount = ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, containerLoc.getWorld().getName());
            stmt.setInt(2, containerLoc.getBlockX());
            stmt.setInt(3, containerLoc.getBlockY());
            stmt.setInt(4, containerLoc.getBlockZ());
            stmt.setInt(5, slot);
            stmt.setString(6, itemType.name());
            stmt.setInt(7, amount);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return UUID.fromString(rs.getString("placer_uuid"));
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to check container item", e);
        }

        return null;
    }

    /**
     * Remove tracking for a specific slot
     */
    public void untrackSlot(Location containerLoc, int slot) {
        String sql = "DELETE FROM container_items WHERE world = ? AND x = ? AND y = ? AND z = ? AND slot = ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, containerLoc.getWorld().getName());
            stmt.setInt(2, containerLoc.getBlockX());
            stmt.setInt(3, containerLoc.getBlockY());
            stmt.setInt(4, containerLoc.getBlockZ());
            stmt.setInt(5, slot);

            stmt.executeUpdate();

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to untrack container slot", e);
        }
    }

    /**
     * Remove all tracking for a container (when container is destroyed)
     */
    public void untrackContainer(Location containerLoc) {
        String sql = "DELETE FROM container_items WHERE world = ? AND x = ? AND y = ? AND z = ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, containerLoc.getWorld().getName());
            stmt.setInt(2, containerLoc.getBlockX());
            stmt.setInt(3, containerLoc.getBlockY());
            stmt.setInt(4, containerLoc.getBlockZ());

            stmt.executeUpdate();

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to untrack container", e);
        }
    }

    /**
     * Get the total number of tracked container items
     */
    public long getTrackedItemCount() {
        String sql = "SELECT COUNT(*) as count FROM container_items";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                return rs.getLong("count");
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get tracked item count", e);
        }

        return 0;
    }

    /**
     * Clean up old container item records (optional maintenance)
     * Removes items placed more than X days ago
     */
    public int cleanupOldItems(int daysOld) {
        long cutoffTime = System.currentTimeMillis() - (daysOld * 24L * 60L * 60L * 1000L);
        String sql = "DELETE FROM container_items WHERE placed_at < ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, cutoffTime);
            int deleted = stmt.executeUpdate();

            if (deleted > 0) {
                plugin.getLogger().info("Cleaned up " + deleted + " old container item records");
            }

            return deleted;

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to cleanup old container items", e);
            return 0;
        }
    }
}
