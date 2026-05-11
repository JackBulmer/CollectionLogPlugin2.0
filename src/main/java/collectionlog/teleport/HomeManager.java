package collectionlog.teleport;

import collectionlog.CollectionLogPlugin;
import collectionlog.database.DatabaseType;
import collectionlog.teleport.data.Home;
import org.bukkit.Location;
import org.bukkit.Material;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class HomeManager {
    private final CollectionLogPlugin plugin;

    public HomeManager(CollectionLogPlugin plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<List<Home>> getHomes(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            List<Home> homes = new ArrayList<>();
            String sql = "SELECT home_name, icon_material, world, x, y, z, yaw, pitch FROM player_homes WHERE player_uuid = ? ORDER BY created_at";
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerId.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String materialName = rs.getString("icon_material");
                        Material mat = Material.RED_BED;
                        if (materialName != null) {
                            try { mat = Material.valueOf(materialName); } catch (IllegalArgumentException ignored) {}
                        }
                        homes.add(new Home(
                            rs.getString("home_name"),
                            mat,
                            rs.getString("world"),
                            rs.getDouble("x"),
                            rs.getDouble("y"),
                            rs.getDouble("z"),
                            rs.getFloat("yaw"),
                            rs.getFloat("pitch")
                        ));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load homes for " + playerId, e);
            }
            return homes;
        });
    }

    public CompletableFuture<Integer> countHomes(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM player_homes WHERE player_uuid = ?";
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerId.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) return rs.getInt(1);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to count homes for " + playerId, e);
            }
            return 0;
        });
    }

    public CompletableFuture<Boolean> homeExists(UUID playerId, String name) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT 1 FROM player_homes WHERE player_uuid = ? AND home_name = ?";
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerId.toString());
                stmt.setString(2, name);
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to check home existence for " + playerId, e);
            }
            return false;
        });
    }

    public CompletableFuture<Home> getHome(UUID playerId, String name) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT home_name, icon_material, world, x, y, z, yaw, pitch FROM player_homes WHERE player_uuid = ? AND home_name = ?";
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerId.toString());
                stmt.setString(2, name);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String materialName = rs.getString("icon_material");
                        Material mat = Material.RED_BED;
                        if (materialName != null) {
                            try { mat = Material.valueOf(materialName); } catch (IllegalArgumentException ignored) {}
                        }
                        return new Home(
                            rs.getString("home_name"),
                            mat,
                            rs.getString("world"),
                            rs.getDouble("x"),
                            rs.getDouble("y"),
                            rs.getDouble("z"),
                            rs.getFloat("yaw"),
                            rs.getFloat("pitch")
                        );
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to get home " + name + " for " + playerId, e);
            }
            return null;
        });
    }

    public CompletableFuture<Void> saveHome(UUID playerId, String name, Material material, Location loc) {
        return CompletableFuture.runAsync(() -> {
            boolean isSQLite = plugin.getDatabaseManager().getDatabaseType() == DatabaseType.SQLITE;
            String sql = isSQLite
                ? "INSERT OR IGNORE INTO player_homes (player_uuid, home_name, icon_material, world, x, y, z, yaw, pitch, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                : "INSERT IGNORE INTO player_homes (player_uuid, home_name, icon_material, world, x, y, z, yaw, pitch, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerId.toString());
                stmt.setString(2, name);
                stmt.setString(3, material.name());
                stmt.setString(4, loc.getWorld().getName());
                stmt.setDouble(5, loc.getX());
                stmt.setDouble(6, loc.getY());
                stmt.setDouble(7, loc.getZ());
                stmt.setFloat(8, loc.getYaw());
                stmt.setFloat(9, loc.getPitch());
                stmt.setLong(10, System.currentTimeMillis());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save home " + name + " for " + playerId, e);
            }
        });
    }

    public CompletableFuture<Void> deleteHome(UUID playerId, String name) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM player_homes WHERE player_uuid = ? AND home_name = ?";
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerId.toString());
                stmt.setString(2, name);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to delete home " + name + " for " + playerId, e);
            }
        });
    }

    public CompletableFuture<Void> updateHomeMaterial(UUID playerId, String name, Material material) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE player_homes SET icon_material = ? WHERE player_uuid = ? AND home_name = ?";
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, material.name());
                stmt.setString(2, playerId.toString());
                stmt.setString(3, name);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to update home material for " + playerId, e);
            }
        });
    }

    public CompletableFuture<List<String>> getHomeNames(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            List<String> names = new ArrayList<>();
            String sql = "SELECT home_name FROM player_homes WHERE player_uuid = ? ORDER BY created_at";
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerId.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) names.add(rs.getString("home_name"));
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to get home names for " + playerId, e);
            }
            return names;
        });
    }
}
