package collectionlog.database.dao;

import collectionlog.database.AsyncTaskManager;
import collectionlog.database.DatabaseManager;
import collectionlog.database.DatabaseType;
import collectionlog.models.PlayerCollectionProgress;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;

/**
 * Data Access Object for player collection progress
 */
public class PlayerCollectionDAO {
    private final Plugin plugin;
    private final DatabaseManager databaseManager;
    private final AsyncTaskManager asyncTaskManager;
    private final ConcurrentMap<UUID, Object> completionLocks = new ConcurrentHashMap<>();

    public PlayerCollectionDAO(Plugin plugin, DatabaseManager databaseManager, AsyncTaskManager asyncTaskManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.asyncTaskManager = asyncTaskManager;
    }

    /**
     * Get player's progress for a specific collection
     */
    public CompletableFuture<PlayerCollectionProgress> getProgress(UUID playerUUID, String collectionId) {
        return asyncTaskManager.runAsync(() -> {
            String sql = "SELECT * FROM player_collections WHERE player_uuid = ? AND collection_id = ?";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerUUID.toString());
                stmt.setString(2, collectionId);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return new PlayerCollectionProgress(
                                playerUUID,
                                rs.getString("collection_id"),
                                rs.getInt("total_items"),
                                rs.getInt("collected_items"),
                                rs.getLong("completion_date"),
                                rs.getLong("last_updated")
                        );
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to get collection progress", e);
            }

            return null;
        });
    }

    /**
     * Check if player has collected a specific item
     */
    public CompletableFuture<Boolean> hasCollectedItem(UUID playerUUID, String collectionId, String itemSignature) {
        return asyncTaskManager.runAsync(() -> {
            String sql = "SELECT COUNT(*) as count FROM collected_items "
                    + "WHERE player_uuid = ? AND collection_id = ? AND item_signature = ?";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerUUID.toString());
                stmt.setString(2, collectionId);
                stmt.setString(3, itemSignature);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("count") > 0;
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to check collected item", e);
            }

            return false;
        });
    }

    /**
     * Add a collected item to player's collection
     */
    public CompletableFuture<Boolean> addCollectedItem(UUID playerUUID, String collectionId, String itemSignature) {
        return asyncTaskManager.runAsync(() -> {
            String sql = insertIgnore("collected_items", "(player_uuid, collection_id, item_signature, first_collected) VALUES (?, ?, ?, ?)");

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerUUID.toString());
                stmt.setString(2, collectionId);
                stmt.setString(3, itemSignature);
                stmt.setLong(4, System.currentTimeMillis());

                int rows = stmt.executeUpdate();
                return rows > 0;

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to add collected item", e);
                return false;
            }
        });
    }

    /**
     * Remove a collected item from player's collection.
     */
    public CompletableFuture<Boolean> removeCollectedItem(UUID playerUUID, String collectionId, String itemSignature) {
        return asyncTaskManager.runAsync(() -> {
            String sql = "DELETE FROM collected_items WHERE player_uuid = ? AND collection_id = ? AND item_signature = ?";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerUUID.toString());
                stmt.setString(2, collectionId);
                stmt.setString(3, itemSignature);
                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to remove collected item", e);
                return false;
            }
        });
    }

    /**
     * Initialize or update player's collection progress
     */
    public CompletableFuture<Void> initializeCollection(UUID playerUUID, String collectionId, int totalItems) {
        return asyncTaskManager.runAsync(() -> {
            String sql = insertIgnore("player_collections", "(player_uuid, collection_id, total_items, collected_items, last_updated) VALUES (?, ?, ?, 0, ?)");

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerUUID.toString());
                stmt.setString(2, collectionId);
                stmt.setInt(3, totalItems);
                stmt.setLong(4, System.currentTimeMillis());

                stmt.executeUpdate();

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to initialize collection", e);
            }

            return null;
        });
    }

    /**
     * Update player's collection progress (increment collected_items count)
     */
    public CompletableFuture<Void> updateProgress(UUID playerUUID, String collectionId) {
        return asyncTaskManager.runAsync(() -> {
            // Count collected items
            String countSql = "SELECT COUNT(*) as count FROM collected_items "
                    + "WHERE player_uuid = ? AND collection_id = ?";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement countStmt = conn.prepareStatement(countSql)) {

                countStmt.setString(1, playerUUID.toString());
                countStmt.setString(2, collectionId);

                int collectedCount = 0;
                try (ResultSet rs = countStmt.executeQuery()) {
                    if (rs.next()) {
                        collectedCount = rs.getInt("count");
                    }
                }

                // Update progress
                String updateSql = "UPDATE player_collections "
                        + "SET collected_items = ?, last_updated = ? "
                        + "WHERE player_uuid = ? AND collection_id = ?";

                try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                    updateStmt.setInt(1, collectedCount);
                    updateStmt.setLong(2, System.currentTimeMillis());
                    updateStmt.setString(3, playerUUID.toString());
                    updateStmt.setString(4, collectionId);

                    updateStmt.executeUpdate();
                }

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to update progress", e);
            }

            return null;
        });
    }

    /**
     * Mark a collection as complete
     */
    public CompletableFuture<Void> markCollectionComplete(UUID playerUUID, String collectionId) {
        return asyncTaskManager.runAsync(() -> {
            String updateSql = "UPDATE player_collections "
                    + "SET completion_date = ? "
                    + "WHERE player_uuid = ? AND collection_id = ?";

            Object lock = completionLocks.computeIfAbsent(playerUUID, ignored -> new Object());
            synchronized (lock) {
                try (Connection conn = databaseManager.getConnection()) {
                    conn.setAutoCommit(false);
                    try {
                        long completionTime = System.currentTimeMillis();
                        try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                            stmt.setLong(1, completionTime);
                            stmt.setString(2, playerUUID.toString());
                            stmt.setString(3, collectionId);
                            stmt.executeUpdate();
                        }

                        int completionOrder = getNextCompletionOrder(conn, playerUUID);
                        String insertSql = insertIgnore(
                                "collection_completions",
                                "(player_uuid, collection_id, completed_at, completion_order) VALUES (?, ?, ?, ?)"
                        );

                        try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                            insertStmt.setString(1, playerUUID.toString());
                            insertStmt.setString(2, collectionId);
                            insertStmt.setLong(3, completionTime);
                            insertStmt.setInt(4, completionOrder);
                            insertStmt.executeUpdate();
                        }

                        conn.commit();
                    } catch (SQLException e) {
                        conn.rollback();
                        throw e;
                    } finally {
                        conn.setAutoCommit(true);
                    }

                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to mark collection complete", e);
                }
            }

            return null;
        });
    }

    /**
     * Clear completion state for a collection.
     */
    public CompletableFuture<Void> clearCollectionCompletion(UUID playerUUID, String collectionId) {
        return asyncTaskManager.runAsync(() -> {
            String updateProgress = "UPDATE player_collections "
                    + "SET completion_date = NULL, last_updated = ? "
                    + "WHERE player_uuid = ? AND collection_id = ?";
            String deleteCompletion = "DELETE FROM collection_completions WHERE player_uuid = ? AND collection_id = ?";

            try (Connection conn = databaseManager.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    try (PreparedStatement progressStmt = conn.prepareStatement(updateProgress)) {
                        progressStmt.setLong(1, System.currentTimeMillis());
                        progressStmt.setString(2, playerUUID.toString());
                        progressStmt.setString(3, collectionId);
                        progressStmt.executeUpdate();
                    }

                    try (PreparedStatement completionStmt = conn.prepareStatement(deleteCompletion)) {
                        completionStmt.setString(1, playerUUID.toString());
                        completionStmt.setString(2, collectionId);
                        completionStmt.executeUpdate();
                    }

                    conn.commit();
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to clear collection completion", e);
            }

            return null;
        });
    }

    /**
     * Reset all collected items and completion state for a collection.
     */
    public CompletableFuture<Void> resetCollectionProgress(UUID playerUUID, String collectionId) {
        return asyncTaskManager.runAsync(() -> {
            String deleteItems = "DELETE FROM collected_items WHERE player_uuid = ? AND collection_id = ?";
            String updateProgress = "UPDATE player_collections "
                    + "SET collected_items = 0, completion_date = NULL, last_updated = ? "
                    + "WHERE player_uuid = ? AND collection_id = ?";
            String deleteCompletion = "DELETE FROM collection_completions WHERE player_uuid = ? AND collection_id = ?";

            try (Connection conn = databaseManager.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    try (PreparedStatement deleteItemsStmt = conn.prepareStatement(deleteItems)) {
                        deleteItemsStmt.setString(1, playerUUID.toString());
                        deleteItemsStmt.setString(2, collectionId);
                        deleteItemsStmt.executeUpdate();
                    }

                    try (PreparedStatement progressStmt = conn.prepareStatement(updateProgress)) {
                        progressStmt.setLong(1, System.currentTimeMillis());
                        progressStmt.setString(2, playerUUID.toString());
                        progressStmt.setString(3, collectionId);
                        progressStmt.executeUpdate();
                    }

                    try (PreparedStatement completionStmt = conn.prepareStatement(deleteCompletion)) {
                        completionStmt.setString(1, playerUUID.toString());
                        completionStmt.setString(2, collectionId);
                        completionStmt.executeUpdate();
                    }

                    conn.commit();
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to reset collection progress", e);
            }

            return null;
        });
    }

    /**
     * Get all completed collections for a player
     */
    public CompletableFuture<List<String>> getCompletedCollections(UUID playerUUID) {
        return asyncTaskManager.runAsync(() -> {
            List<String> completed = new ArrayList<>();
            String sql = "SELECT collection_id FROM player_collections "
                    + "WHERE player_uuid = ? AND (completion_date IS NOT NULL OR collected_items >= total_items)";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerUUID.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        completed.add(rs.getString("collection_id"));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to get completed collections", e);
            }

            return completed;
        });
    }

    /**
     * Get player's total completion count
     */
    public CompletableFuture<Integer> getCompletionCount(UUID playerUUID) {
        return asyncTaskManager.runAsync(() -> {
            String sql = "SELECT COUNT(*) as count FROM collection_completions WHERE player_uuid = ?";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerUUID.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("count");
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to get completion count", e);
            }

            return 0;
        });
    }

    /**
     * Get all items collected in a specific collection
     */
    public CompletableFuture<List<String>> getCollectedItems(UUID playerUUID, String collectionId) {
        return asyncTaskManager.runAsync(() -> {
            List<String> items = new ArrayList<>();
            String sql = "SELECT item_signature FROM collected_items "
                    + "WHERE player_uuid = ? AND collection_id = ?";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerUUID.toString());
                stmt.setString(2, collectionId);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        items.add(rs.getString("item_signature"));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to get collected items", e);
            }

            return items;
        });
    }

    private String insertIgnore(String table, String columnsAndValues) {
        if (databaseManager.getDatabaseType() == DatabaseType.MYSQL) {
            return "INSERT IGNORE INTO " + table + " " + columnsAndValues;
        }
        return "INSERT OR IGNORE INTO " + table + " " + columnsAndValues;
    }

    private int getNextCompletionOrder(Connection conn, UUID playerUUID) throws SQLException {
        String sql = "SELECT COALESCE(MAX(completion_order), 0) + 1 AS next_order "
                + "FROM collection_completions WHERE player_uuid = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUUID.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("next_order");
                }
            }
        }
        return 1;
    }
}
