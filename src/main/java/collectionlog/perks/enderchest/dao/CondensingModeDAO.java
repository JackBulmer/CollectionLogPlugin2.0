package collectionlog.perks.enderchest.dao;

import collectionlog.CollectionLogPlugin;
import collectionlog.database.DatabaseManager;
import collectionlog.database.DatabaseType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class CondensingModeDAO {
    private final CollectionLogPlugin plugin;
    private final DatabaseManager databaseManager;

    public CondensingModeDAO(CollectionLogPlugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    public void markPlayerForCondensing(UUID playerId) {
        String sql = upsertSql();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerId.toString());
            stmt.setLong(2, System.currentTimeMillis());
            if (databaseManager.getDatabaseType() == DatabaseType.MYSQL) {
                stmt.setLong(3, System.currentTimeMillis());
            }
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to mark player for ender chest condensing: " + e.getMessage());
        }
    }

    public void removeCondensingFlag(UUID playerId) {
        String sql = "DELETE FROM ender_chest_condensing WHERE player_uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerId.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to remove ender chest condensing flag: " + e.getMessage());
        }
    }

    public Set<UUID> loadAllCondensingPlayers() {
        String sql = "SELECT player_uuid FROM ender_chest_condensing";
        Set<UUID> players = new HashSet<>();

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                try {
                    players.add(UUID.fromString(rs.getString("player_uuid")));
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load ender chest condensing players: " + e.getMessage());
        }

        return players;
    }

    private String upsertSql() {
        if (databaseManager.getDatabaseType() == DatabaseType.MYSQL) {
            return "INSERT INTO ender_chest_condensing (player_uuid, marked_at) VALUES (?, ?) "
                    + "ON DUPLICATE KEY UPDATE marked_at = ?";
        }
        return "INSERT OR REPLACE INTO ender_chest_condensing (player_uuid, marked_at) VALUES (?, ?)";
    }
}
