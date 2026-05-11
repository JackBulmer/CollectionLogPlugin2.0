package collectionlog.teleport;

import collectionlog.CollectionLogPlugin;
import collectionlog.database.DatabaseType;
import collectionlog.teleport.data.TeleportSettings;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class TeleportSettingsManager {
    private final CollectionLogPlugin plugin;

    public TeleportSettingsManager(CollectionLogPlugin plugin) {
        this.plugin = plugin;
    }

    private TeleportSettings getConfigDefaults() {
        int maxHomes = plugin.getConfig().getInt("teleport.max_homes", 5);
        int tpCost = plugin.getConfig().getInt("teleport.tp_cost", 5);
        boolean interDimensional = plugin.getConfig().getBoolean("teleport.inter_dimensional_teleporting", true);
        int countdown = plugin.getConfig().getInt("teleport.countdown_seconds", 5);
        return new TeleportSettings(maxHomes, tpCost, interDimensional, countdown);
    }

    public CompletableFuture<TeleportSettings> getSettings(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT max_homes, tp_cost, inter_dimensional, countdown_seconds FROM player_teleport_settings WHERE player_uuid = ?";
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerId.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return new TeleportSettings(
                            rs.getInt("max_homes"),
                            rs.getInt("tp_cost"),
                            rs.getBoolean("inter_dimensional"),
                            rs.getInt("countdown_seconds")
                        );
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load teleport settings for " + playerId, e);
            }
            return getConfigDefaults();
        });
    }

    public CompletableFuture<Void> saveSettings(UUID playerId, TeleportSettings settings) {
        return CompletableFuture.runAsync(() -> {
            boolean isSQLite = plugin.getDatabaseManager().getDatabaseType() == DatabaseType.SQLITE;
            String sql = isSQLite
                ? "INSERT OR REPLACE INTO player_teleport_settings (player_uuid, max_homes, tp_cost, inter_dimensional, countdown_seconds) VALUES (?, ?, ?, ?, ?)"
                : "INSERT INTO player_teleport_settings (player_uuid, max_homes, tp_cost, inter_dimensional, countdown_seconds) VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE max_homes=VALUES(max_homes), tp_cost=VALUES(tp_cost), inter_dimensional=VALUES(inter_dimensional), countdown_seconds=VALUES(countdown_seconds)";
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerId.toString());
                stmt.setInt(2, settings.getMaxHomes());
                stmt.setInt(3, settings.getTpCost());
                stmt.setBoolean(4, settings.isInterDimensional());
                stmt.setInt(5, settings.getCountdownSeconds());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save teleport settings for " + playerId, e);
            }
        });
    }

    public CompletableFuture<Void> resetSettings(UUID playerId) {
        return saveSettings(playerId, getConfigDefaults());
    }
}
