package collectionlog.stats;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import collectionlog.CollectionLogPlugin;
import collectionlog.collections.CollectionDefinition;
import collectionlog.leaderboard.LeaderboardManager;
import collectionlog.leaderboard.PlayerStats;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class CollectionStatsFileManager {
    private final CollectionLogPlugin plugin;
    private final LeaderboardManager leaderboardManager;
    private final Gson gson;
    private final Path statsFilePath;
    private final Object fileLock = new Object();
    private final Map<UUID, StatsFileEntry> cachedEntries = new ConcurrentHashMap<>();

    public CollectionStatsFileManager(CollectionLogPlugin plugin, LeaderboardManager leaderboardManager) {
        this.plugin = plugin;
        this.leaderboardManager = leaderboardManager;
        this.gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        this.statsFilePath = plugin.getDataFolder().toPath().getParent().resolve("player_collection_stats.json");
    }

    public void generateStatsFile() {
        leaderboardManager.getAllPlayerStats().thenAccept(playerStats ->
                plugin.getAsyncTaskManager().runAsync(() -> {
                    Map<UUID, StatsFileEntry> rebuiltEntries = new LinkedHashMap<>();
                    for (PlayerStats stats : playerStats) {
                        rebuiltEntries.put(stats.playerId(), buildEntry(stats));
                    }

                    synchronized (fileLock) {
                        cachedEntries.clear();
                        cachedEntries.putAll(rebuiltEntries);
                        writeEntries(new LinkedHashMap<>(cachedEntries));
                    }
                }).exceptionally(throwable -> {
                    plugin.getLogger().log(Level.SEVERE, "Failed to regenerate collection stats file", throwable);
                    return null;
                })
        );
    }

    public void updatePlayerStats(UUID playerId) {
        updatePlayerStats(playerId, null);
    }

    public void updatePlayerStats(UUID playerId, String usernameHint) {
        leaderboardManager.refreshPlayerStats(playerId, usernameHint)
                .thenAccept(stats -> {
                    StatsFileEntry entry = buildEntry(stats);
                    synchronized (fileLock) {
                        cachedEntries.put(playerId, entry);
                        writeEntries(new LinkedHashMap<>(cachedEntries));
                    }
                })
                .exceptionally(throwable -> {
                    plugin.getLogger().log(Level.SEVERE, "Failed to update collection stats file entry for " + playerId, throwable);
                    return null;
                });
    }

    private StatsFileEntry buildEntry(PlayerStats playerStats) {
        return new StatsFileEntry(
                playerStats.username(),
                playerStats.totalCollected(),
                Instant.now().toString(),
                getCompletedCollections(playerStats.playerId())
        );
    }

    private List<String> getCompletedCollections(UUID playerId) {
        if (plugin.getMilestoneManager() == null) {
            return List.of();
        }

        Set<String> completedMilestones = plugin.getMilestoneManager().getPlayerCompletedMilestones(playerId);
        return completedMilestones.stream()
                .filter(name -> !name.contains(":"))
                .map(this::formatCompletedCollection)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private String formatCompletedCollection(String milestoneName) {
        CollectionDefinition specialCollection = plugin.getSpecialCollectionLoader().getSpecialCollection(milestoneName);
        if (specialCollection != null) {
            return specialCollection.getName();
        }
        return milestoneName;
    }

    private void writeEntries(Map<UUID, StatsFileEntry> entries) {
        try {
            Files.createDirectories(statsFilePath.getParent());

            JsonObject root = new JsonObject();
            List<Map.Entry<UUID, StatsFileEntry>> orderedEntries = new ArrayList<>(entries.entrySet());
            orderedEntries.sort(Comparator.comparing(entry -> entry.getKey().toString()));

            for (Map.Entry<UUID, StatsFileEntry> entry : orderedEntries) {
                JsonObject playerObject = new JsonObject();
                playerObject.addProperty("username", entry.getValue().username());
                playerObject.addProperty("total_collected", entry.getValue().totalCollected());
                playerObject.addProperty("last_updated", entry.getValue().lastUpdated());

                JsonArray completedCollections = new JsonArray();
                for (String collectionName : entry.getValue().completedCollections()) {
                    completedCollections.add(collectionName);
                }
                playerObject.add("completed_collections", completedCollections);

                root.add(entry.getKey().toString(), playerObject);
            }

            String json = gson.toJson(root);
            Path tempFile = statsFilePath.resolveSibling(statsFilePath.getFileName() + ".tmp");
            Files.writeString(tempFile, json);

            try {
                Files.move(tempFile, statsFilePath,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(tempFile, statsFilePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to write player_collection_stats.json", exception);
        }
    }

    private record StatsFileEntry(
            String username,
            int totalCollected,
            String lastUpdated,
            List<String> completedCollections
    ) {
    }
}
