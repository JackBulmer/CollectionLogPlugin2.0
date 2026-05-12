package collectionlog.leaderboard;

import collectionlog.CollectionLogPlugin;
import collectionlog.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class HologramManager {
    private static final double LINE_SPACING = 0.25D;
    private static final long REFRESH_INTERVAL_TICKS = 1200L;
    private static final double REMOVE_DISTANCE = 10.0D;
    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getIntegerInstance(java.util.Locale.US);

    private final CollectionLogPlugin plugin;
    private final LeaderboardManager leaderboardManager;
    private final DatabaseManager databaseManager;
    private final Map<Long, LeaderboardHologram> holograms = new ConcurrentHashMap<>();

    private int refreshTaskId = -1;

    public HologramManager(CollectionLogPlugin plugin, LeaderboardManager leaderboardManager) {
        this.plugin = plugin;
        this.leaderboardManager = leaderboardManager;
        this.databaseManager = plugin.getDatabaseManager();
    }

    public void start() {
        if (refreshTaskId != -1) {
            return;
        }

        refreshTaskId = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(
                plugin,
                this::updateHolograms,
                REFRESH_INTERVAL_TICKS,
                REFRESH_INTERVAL_TICKS
        );
    }

    public void shutdown() {
        if (refreshTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(refreshTaskId);
            refreshTaskId = -1;
        }

        for (LeaderboardHologram hologram : holograms.values()) {
            removeArmorStands(hologram);
        }
        holograms.clear();
    }

    public CompletableFuture<Boolean> createHologram(Location location) {
        Location storedLocation = location.clone();
        String worldName = storedLocation.getWorld() == null ? null : storedLocation.getWorld().getName();
        if (worldName == null) {
            return CompletableFuture.completedFuture(false);
        }

        return plugin.getAsyncTaskManager().runAsync(() -> insertHologram(storedLocation, worldName))
                .thenApply(hologramId -> {
                    if (hologramId == null) {
                        return false;
                    }

                    plugin.getAsyncTaskManager().runSync(() -> {
                        holograms.put(hologramId, new LeaderboardHologram(
                                hologramId,
                                worldName,
                                storedLocation.getX(),
                                storedLocation.getY(),
                                storedLocation.getZ(),
                                System.currentTimeMillis()
                        ));
                        updateHolograms();
                    });
                    return true;
                });
    }

    public CompletableFuture<Boolean> removeNearestHologram(Location location) {
        if (location.getWorld() == null) {
            return CompletableFuture.completedFuture(false);
        }

        LeaderboardHologram nearest = holograms.values().stream()
                .filter(hologram -> hologram.worldName().equals(location.getWorld().getName()))
                .min(Comparator.comparingDouble(hologram -> hologram.location(location.getWorld()).distanceSquared(location)))
                .orElse(null);

        if (nearest == null || nearest.location(location.getWorld()).distanceSquared(location) > REMOVE_DISTANCE * REMOVE_DISTANCE) {
            return CompletableFuture.completedFuture(false);
        }

        return plugin.getAsyncTaskManager().runAsync(() -> deleteHologram(nearest.id()))
                .thenApply(deleted -> {
                    if (!deleted) {
                        return false;
                    }

                    plugin.getAsyncTaskManager().runSync(() -> {
                        LeaderboardHologram removed = holograms.remove(nearest.id());
                        if (removed != null) {
                            removeArmorStands(removed);
                        }
                    });
                    return true;
                });
    }

    public CompletableFuture<Void> loadHologramsFromDatabase() {
        CompletableFuture<Void> future = new CompletableFuture<>();

        plugin.getAsyncTaskManager().runAsync(this::loadStoredHolograms)
                .whenComplete((loadedHolograms, throwable) -> {
                    if (throwable != null) {
                        future.completeExceptionally(throwable);
                        return;
                    }

                    plugin.getAsyncTaskManager().runSync(() -> {
                        try {
                            holograms.clear();
                            for (LeaderboardHologram hologram : loadedHolograms) {
                                if (Bukkit.getWorld(hologram.worldName()) == null) {
                                    deleteInvalidWorldHologram(hologram.id());
                                    continue;
                                }
                                holograms.put(hologram.id(), hologram);
                            }
                            future.complete(null);
                        } catch (Exception exception) {
                            future.completeExceptionally(exception);
                        }
                    });
                });

        return future.exceptionally(throwable -> {
            plugin.getLogger().log(Level.SEVERE, "Failed to load leaderboard holograms", throwable);
            return null;
        });
    }

    public void updateHolograms() {
        leaderboardManager.getTopPlayers(10)
                .thenAccept(topPlayers -> plugin.getAsyncTaskManager().runSync(() -> {
                    List<String> lines = buildLeaderboardLines(topPlayers);
                    for (LeaderboardHologram hologram : holograms.values()) {
                        World world = Bukkit.getWorld(hologram.worldName());
                        if (world == null) {
                            deleteInvalidWorldHologram(hologram.id());
                            continue;
                        }

                        Location location = hologram.location(world);
                        int chunkX = location.getBlockX() >> 4;
                        int chunkZ = location.getBlockZ() >> 4;
                        if (!world.isChunkLoaded(chunkX, chunkZ)) {
                            continue;
                        }

                        renderHologram(hologram, location, lines);
                    }
                }));
    }

    private void renderHologram(LeaderboardHologram hologram, Location baseLocation, List<String> lines) {
        removeArmorStands(hologram);

        for (int i = 0; i < lines.size(); i++) {
            int lineIndex = i;
            double yOffset = (lines.size() - 1 - i) * LINE_SPACING;
            Location lineLocation = baseLocation.clone().add(0.0D, yOffset, 0.0D);
            ArmorStand armorStand = baseLocation.getWorld().spawn(lineLocation, ArmorStand.class, stand -> {
                stand.setVisible(false);
                stand.setGravity(false);
                stand.setMarker(true);
                stand.setSmall(true);
                stand.setCustomName(lines.get(lineIndex));
                stand.setCustomNameVisible(true);
            });
            hologram.armorStandIds().add(armorStand.getUniqueId());
        }
    }

    private void removeArmorStands(LeaderboardHologram hologram) {
        for (UUID entityId : hologram.armorStandIds()) {
            Entity entity = Bukkit.getEntity(entityId);
            if (entity != null) {
                entity.remove();
            }
        }
        hologram.armorStandIds().clear();
    }

    private List<String> buildLeaderboardLines(List<PlayerStats> topPlayers) {
        List<String> lines = new ArrayList<>();
        lines.add("§6§l=== Collection Log Top 10 ===");

        if (topPlayers.isEmpty()) {
            lines.add("§7No ranked players yet.");
            return lines;
        }

        for (int i = 0; i < topPlayers.size(); i++) {
            PlayerStats playerStats = topPlayers.get(i);
            lines.add("§e" + (i + 1) + ". §f" + playerStats.username() + " §7- §e"
                    + NUMBER_FORMAT.format(playerStats.totalCollected()) + " items");
        }

        return lines;
    }

    private Long insertHologram(Location location, String worldName) {
        String sql = "INSERT INTO leaderboard_holograms (world, x, y, z, created_at) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, worldName);
            stmt.setDouble(2, location.getX());
            stmt.setDouble(3, location.getY());
            stmt.setDouble(4, location.getZ());
            stmt.setLong(5, System.currentTimeMillis());
            stmt.executeUpdate();

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to insert leaderboard hologram", e);
        }

        return null;
    }

    private boolean deleteHologram(long hologramId) {
        String sql = "DELETE FROM leaderboard_holograms WHERE id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, hologramId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to delete leaderboard hologram " + hologramId, e);
            return false;
        }
    }

    private void deleteInvalidWorldHologram(long hologramId) {
        LeaderboardHologram removed = holograms.remove(hologramId);
        if (removed != null) {
            removeArmorStands(removed);
        }
        plugin.getAsyncTaskManager().runAsync(() -> deleteHologram(hologramId));
    }

    private List<LeaderboardHologram> loadStoredHolograms() {
        List<LeaderboardHologram> stored = new ArrayList<>();
        String sql = "SELECT id, world, x, y, z, created_at FROM leaderboard_holograms ORDER BY created_at ASC";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                stored.add(new LeaderboardHologram(
                        rs.getLong("id"),
                        rs.getString("world"),
                        rs.getDouble("x"),
                        rs.getDouble("y"),
                        rs.getDouble("z"),
                        rs.getLong("created_at")
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load stored leaderboard holograms", e);
        }

        return stored;
    }

    private record LeaderboardHologram(
            long id,
            String worldName,
            double x,
            double y,
            double z,
            long createdAt,
            List<UUID> armorStandIds
    ) {
        private LeaderboardHologram(long id, String worldName, double x, double y, double z, long createdAt) {
            this(id, worldName, x, y, z, createdAt, new ArrayList<>());
        }

        private Location location(World world) {
            return new Location(world, x, y, z);
        }
    }
}
