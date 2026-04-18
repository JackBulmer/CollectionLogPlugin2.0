package collectionlog.models;

import java.util.UUID;

/**
 * Represents a player's progress for a specific collection
 */
public class PlayerCollectionProgress {
    private final UUID playerUUID;
    private final String collectionId;
    private final int totalItems;
    private final int collectedItems;
    private final long completionDate;
    private final long lastUpdated;

    public PlayerCollectionProgress(UUID playerUUID, String collectionId, int totalItems,
                                    int collectedItems, long completionDate, long lastUpdated) {
        this.playerUUID = playerUUID;
        this.collectionId = collectionId;
        this.totalItems = totalItems;
        this.collectedItems = collectedItems;
        this.completionDate = completionDate;
        this.lastUpdated = lastUpdated;
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public String getCollectionId() {
        return collectionId;
    }

    public int getTotalItems() {
        return totalItems;
    }

    public int getCollectedItems() {
        return collectedItems;
    }

    public boolean isComplete() {
        return completionDate > 0 || collectedItems >= totalItems;
    }

    public long getCompletionDate() {
        return completionDate;
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public double getPercentage() {
        if (totalItems == 0) {
            return 0.0;
        }
        if (collectedItems >= totalItems) {
            return 100.0;
        }
        return (collectedItems * 100.0) / totalItems;
    }

    @Override
    public String toString() {
        return "Progress{" +
                "collection='" + collectionId + '\'' +
                ", progress=" + collectedItems + "/" + totalItems +
                " (" + getPercentage() + "%)" +
                ", complete=" + isComplete() +
                '}';
    }
}
