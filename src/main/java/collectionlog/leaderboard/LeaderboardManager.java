package collectionlog.leaderboard;

import collectionlog.CollectionLogPlugin;
import collectionlog.database.AsyncTaskManager;
import collectionlog.database.DatabaseManager;
import collectionlog.database.DatabaseType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class LeaderboardManager {
    private static final long CACHE_TTL_MS = 30_000L;

    private final CollectionLogPlugin plugin;
    private final DatabaseManager databaseManager;
    private final AsyncTaskManager asyncTaskManager;
    private final Object cacheLock = new Object();

    private volatile List<PlayerStats> cachedRankedPlayers = List.of();
    private volatile long cacheExpiry = 0L;
    private volatile CompletableFuture<List<PlayerStats>> inFlightRefresh;

    public LeaderboardManager(CollectionLogPlugin plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        this.asyncTaskManager = plugin.getAsyncTaskManager();
    }

    public CompletableFuture<List<PlayerStats>> getTopPlayers(int limit) {
        return getRankedPlayers().thenApply(players -> players.subList(0, Math.min(limit, players.size())));
    }

    public CompletableFuture<PlayerRank> getPlayerRank(UUID playerId) {
        return getPlayerStats(playerId).thenCombine(getRankedPlayers(), (playerStats, rankedPlayers) -> {
            if (playerStats.totalCollected() <= 0) {
                return PlayerRank.unranked(playerStats);
            }

            for (int i = 0; i < rankedPlayers.size(); i++) {
                if (rankedPlayers.get(i).playerId().equals(playerId)) {
                    return PlayerRank.ranked(i + 1, rankedPlayers.get(i));
                }
            }

            int rank = 1;
            String playerUuid = playerId.toString();
            for (PlayerStats rankedPlayer : rankedPlayers) {
                if (rankedPlayer.totalCollected() > playerStats.totalCollected()) {
                    rank++;
                    continue;
                }

                if (rankedPlayer.totalCollected() == playerStats.totalCollected()
                        && rankedPlayer.playerId().toString().compareTo(playerUuid) < 0) {
                    rank++;
                }
            }

            return PlayerRank.ranked(rank, playerStats);
        });
    }

    public CompletableFuture<PlayerStats> getPlayerStats(UUID playerId) {
        return asyncTaskManager.runAsync(() -> {
            String sql = "SELECT username, total_collected, last_updated FROM player_stats WHERE player_uuid = ?";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerId.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return new PlayerStats(
                                playerId,
                                defaultUsername(rs.getString("username"), playerId),
                                rs.getInt("total_collected"),
                                Instant.ofEpochMilli(rs.getLong("last_updated"))
                        );
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load player stats for " + playerId, e);
            }

            return new PlayerStats(playerId, playerId.toString(), 0, Instant.EPOCH);
        });
    }

    public CompletableFuture<List<PlayerStats>> getAllPlayerStats() {
        return asyncTaskManager.runAsync(() -> {
            List<PlayerStats> players = new ArrayList<>();
            String sql = "SELECT player_uuid, username, total_collected, last_updated FROM player_stats ORDER BY player_uuid ASC";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    UUID playerId = parseUuid(rs.getString("player_uuid"));
                    if (playerId == null) {
                        continue;
                    }

                    players.add(new PlayerStats(
                            playerId,
                            defaultUsername(rs.getString("username"), playerId),
                            rs.getInt("total_collected"),
                            Instant.ofEpochMilli(rs.getLong("last_updated"))
                    ));
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load all player stats", e);
            }

            return players;
        });
    }

    public CompletableFuture<PlayerStats> refreshPlayerStats(UUID playerId) {
        return refreshPlayerStats(playerId, null);
    }

    public CompletableFuture<PlayerStats> refreshPlayerStats(UUID playerId, String usernameHint) {
        return asyncTaskManager.runAsync(() -> refreshPlayerStats(playerId, usernameHint, System.currentTimeMillis()))
                .thenApply(stats -> {
                    invalidateCache();
                    return stats;
                });
    }

    public CompletableFuture<Void> refreshAllPlayerStats() {
        return asyncTaskManager.runAsync(() -> {
            long now = System.currentTimeMillis();

            try (Connection conn = databaseManager.getConnection()) {
                Set<UUID> playerIds = loadKnownPlayers(conn);
                for (UUID playerId : playerIds) {
                    refreshPlayerStats(playerId, null, now, conn);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to refresh leaderboard stats", e);
            }

            invalidateCache();
            return null;
        });
    }

    public void invalidateCache() {
        cachedRankedPlayers = List.of();
        cacheExpiry = 0L;
    }

    private CompletableFuture<List<PlayerStats>> getRankedPlayers() {
        long now = System.currentTimeMillis();
        List<PlayerStats> currentCache = cachedRankedPlayers;
        if (now < cacheExpiry) {
            return CompletableFuture.completedFuture(currentCache);
        }

        synchronized (cacheLock) {
            long refreshedNow = System.currentTimeMillis();
            List<PlayerStats> refreshedCache = cachedRankedPlayers;
            if (refreshedNow < cacheExpiry) {
                return CompletableFuture.completedFuture(refreshedCache);
            }

            if (inFlightRefresh != null && !inFlightRefresh.isDone()) {
                return inFlightRefresh;
            }

            inFlightRefresh = asyncTaskManager.runAsync(this::loadRankedPlayers)
                    .thenApply(players -> {
                        cachedRankedPlayers = List.copyOf(players);
                        cacheExpiry = System.currentTimeMillis() + CACHE_TTL_MS;
                        return cachedRankedPlayers;
                    });

            inFlightRefresh.whenComplete((ignored, throwable) -> {
                synchronized (cacheLock) {
                    inFlightRefresh = null;
                }
            });

            return inFlightRefresh;
        }
    }

    private List<PlayerStats> loadRankedPlayers() {
        List<PlayerStats> players = new ArrayList<>();
        String sql = "SELECT player_uuid, username, total_collected, last_updated FROM player_stats "
                + "WHERE total_collected > 0 ORDER BY total_collected DESC, player_uuid ASC";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                UUID playerId = parseUuid(rs.getString("player_uuid"));
                if (playerId == null) {
                    continue;
                }

                players.add(new PlayerStats(
                        playerId,
                        defaultUsername(rs.getString("username"), playerId),
                        rs.getInt("total_collected"),
                        Instant.ofEpochMilli(rs.getLong("last_updated"))
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load ranked players", e);
        }

        return players;
    }

    private PlayerStats refreshPlayerStats(UUID playerId, String usernameHint, long now) throws SQLException {
        try (Connection conn = databaseManager.getConnection()) {
            return refreshPlayerStats(playerId, usernameHint, now, conn);
        }
    }

    private PlayerStats refreshPlayerStats(UUID playerId, String usernameHint, long now, Connection conn) throws SQLException {
        int totalCollected = countCollectedItems(conn, playerId);
        String username = resolveUsername(conn, playerId, usernameHint);

        try (PreparedStatement stmt = conn.prepareStatement(upsertStatsSql())) {
            stmt.setString(1, playerId.toString());
            stmt.setString(2, username);
            stmt.setInt(3, totalCollected);
            stmt.setLong(4, now);
            stmt.executeUpdate();
        }

        return new PlayerStats(playerId, username, totalCollected, Instant.ofEpochMilli(now));
    }

    private int countCollectedItems(Connection conn, UUID playerId) throws SQLException {
        String sql = "SELECT COUNT(*) AS count FROM collected_items WHERE player_uuid = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("count");
                }
            }
        }
        return 0;
    }

    private String resolveUsername(Connection conn, UUID playerId, String usernameHint) throws SQLException {
        if (usernameHint != null && !usernameHint.isBlank()) {
            return usernameHint;
        }

        String currentStatsUsername = querySingleString(conn, "SELECT username FROM player_stats WHERE player_uuid = ?", playerId);
        if (currentStatsUsername != null && !currentStatsUsername.isBlank()) {
            return currentStatsUsername;
        }

        String playerDataUsername = querySingleString(conn, "SELECT username FROM player_data WHERE player_uuid = ?", playerId);
        return defaultUsername(playerDataUsername, playerId);
    }

    private String querySingleString(Connection conn, String sql, UUID playerId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        }
        return null;
    }

    private Set<UUID> loadKnownPlayers(Connection conn) throws SQLException {
        Set<UUID> playerIds = new LinkedHashSet<>();
        String sql = "SELECT player_uuid FROM player_stats "
                + "UNION SELECT player_uuid FROM collected_items "
                + "UNION SELECT player_uuid FROM completed_milestones "
                + "UNION SELECT player_uuid FROM player_data";

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                UUID playerId = parseUuid(rs.getString("player_uuid"));
                if (playerId != null) {
                    playerIds.add(playerId);
                }
            }
        }

        return playerIds;
    }

    private String upsertStatsSql() {
        if (databaseManager.getDatabaseType() == DatabaseType.MYSQL) {
            return "INSERT INTO player_stats (player_uuid, username, total_collected, last_updated) VALUES (?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE username = VALUES(username), total_collected = VALUES(total_collected), last_updated = VALUES(last_updated)";
        }

        return "INSERT INTO player_stats (player_uuid, username, total_collected, last_updated) VALUES (?, ?, ?, ?) "
                + "ON CONFLICT(player_uuid) DO UPDATE SET username = excluded.username, total_collected = excluded.total_collected, last_updated = excluded.last_updated";
    }

    private String defaultUsername(String username, UUID playerId) {
        return username == null || username.isBlank() ? playerId.toString() : username;
    }

    private UUID parseUuid(String rawUuid) {
        try {
            return UUID.fromString(rawUuid);
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Skipping invalid UUID in leaderboard data: " + rawUuid);
            return null;
        }
    }

    public record PlayerRank(int rank, PlayerStats playerStats, boolean ranked) {
        public static PlayerRank ranked(int rank, PlayerStats playerStats) {
            return new PlayerRank(rank, playerStats, true);
        }

        public static PlayerRank unranked(PlayerStats playerStats) {
            return new PlayerRank(-1, playerStats, false);
        }
    }
}
