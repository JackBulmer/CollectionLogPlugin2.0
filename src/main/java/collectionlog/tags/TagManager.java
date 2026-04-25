package collectionlog.tags;

import collectionlog.CollectionLogPlugin;
import collectionlog.database.dao.PlayerCollectionDAO;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Manages all available tags and player tag selections.
 * Tag definitions are loaded from milestones.yml via MilestoneManager and are the
 * single source of truth – no tags are hardcoded here.
 */
public class TagManager {
    private final CollectionLogPlugin plugin;
    private final PlayerCollectionDAO playerCollectionDAO;
    private List<Tag> allTags;

    public TagManager(CollectionLogPlugin plugin) {
        this.plugin = plugin;
        this.playerCollectionDAO = plugin.getPlayerCollectionDAO();
        this.allTags = loadTagsFromMilestones();
    }

    /**
     * Loads all tag definitions from MilestoneManager (which reads milestones.yml).
     */
    private List<Tag> loadTagsFromMilestones() {
        if (plugin.getMilestoneManager() == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(plugin.getMilestoneManager().getAllTags());
    }

    public List<Tag> getAllTags() {
        return new ArrayList<>(allTags);
    }

    /**
     * Returns unlocked tags for a player given their pre-fetched completed collection IDs.
     */
    public List<Tag> getUnlockedTags(List<String> completedCollections) {
        return allTags.stream()
                .filter(tag -> isTagUnlocked(tag, completedCollections))
                .collect(Collectors.toList());
    }

    /**
     * Checks whether a tag is unlocked given a pre-fetched list of completed collection IDs.
     */
    public boolean isTagUnlocked(Tag tag, List<String> completedCollections) {
        if (!tag.hasRequirement()) {
            return true;
        }
        return completedCollections.contains(tag.getRequiredCollection());
    }

    public CompletableFuture<List<String>> getCompletedCollections(UUID playerId) {
        return playerCollectionDAO.getCompletedCollections(playerId);
    }

    public CompletableFuture<String> getPlayerPrefix(UUID playerId) {
        return playerCollectionDAO.getPlayerPrefix(playerId);
    }

    public CompletableFuture<String> getPlayerSuffix(UUID playerId) {
        return playerCollectionDAO.getPlayerSuffix(playerId);
    }

    public CompletableFuture<Void> setPlayerPrefix(UUID playerId, String prefix) {
        return playerCollectionDAO.setPlayerPrefix(playerId, prefix);
    }

    public CompletableFuture<Void> setPlayerSuffix(UUID playerId, String suffix) {
        return playerCollectionDAO.setPlayerSuffix(playerId, suffix);
    }
}
