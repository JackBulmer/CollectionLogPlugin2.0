package collectionlog.teleport;

import collectionlog.CollectionLogPlugin;
import collectionlog.database.DatabaseType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class HubManager {
    private static final int HUB_ROW_ID = 1;

    private final CollectionLogPlugin plugin;

    public HubManager(CollectionLogPlugin plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<Void> setHub(Location location, UUID setBy) {
        return plugin.getAsyncTaskManager().runAsync(() -> {
            if (location.getWorld() == null) {
                throw new IllegalArgumentException("Hub world cannot be null");
            }
            boolean sqlite = plugin.getDatabaseManager().getDatabaseType() == DatabaseType.SQLITE;
            String sql = sqlite
                    ? "INSERT OR REPLACE INTO server_hub (id, world, x, y, z, yaw, pitch, set_by, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
                    : "INSERT INTO server_hub (id, world, x, y, z, yaw, pitch, set_by, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE world=VALUES(world), x=VALUES(x), y=VALUES(y), z=VALUES(z), yaw=VALUES(yaw), pitch=VALUES(pitch), set_by=VALUES(set_by), updated_at=VALUES(updated_at)";
            try (Connection connection = plugin.getDatabaseManager().getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, HUB_ROW_ID);
                statement.setString(2, location.getWorld().getName());
                statement.setDouble(3, location.getX());
                statement.setDouble(4, location.getY());
                statement.setDouble(5, location.getZ());
                statement.setFloat(6, location.getYaw());
                statement.setFloat(7, location.getPitch());
                statement.setString(8, setBy != null ? setBy.toString() : null);
                statement.setLong(9, System.currentTimeMillis());
                statement.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to set hub location", e);
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<Location> getHub() {
        return plugin.getAsyncTaskManager().runAsync(() -> {
            String sql = "SELECT world, x, y, z, yaw, pitch FROM server_hub WHERE id = ?";
            try (Connection connection = plugin.getDatabaseManager().getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, HUB_ROW_ID);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return null;
                    }
                    World world = Bukkit.getWorld(resultSet.getString("world"));
                    if (world == null) {
                        return null;
                    }
                    return new Location(
                            world,
                            resultSet.getDouble("x"),
                            resultSet.getDouble("y"),
                            resultSet.getDouble("z"),
                            resultSet.getFloat("yaw"),
                            resultSet.getFloat("pitch")
                    );
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load hub location", e);
                return null;
            }
        });
    }

    public CompletableFuture<Boolean> hasHub() {
        return plugin.getAsyncTaskManager().runAsync(() -> {
            String sql = "SELECT 1 FROM server_hub WHERE id = ?";
            try (Connection connection = plugin.getDatabaseManager().getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, HUB_ROW_ID);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next();
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to check hub existence", e);
                return false;
            }
        });
    }

    public CompletableFuture<Void> deleteHub() {
        return plugin.getAsyncTaskManager().runAsync(() -> {
            String sql = "DELETE FROM server_hub WHERE id = ?";
            try (Connection connection = plugin.getDatabaseManager().getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, HUB_ROW_ID);
                statement.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to delete hub location", e);
                throw new RuntimeException(e);
            }
        });
    }
}
