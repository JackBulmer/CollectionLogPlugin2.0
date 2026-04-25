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
 */
public class TagManager {
    private final CollectionLogPlugin plugin;
    private final PlayerCollectionDAO playerCollectionDAO;
    private final List<Tag> allTags;

    public TagManager(CollectionLogPlugin plugin) {
        this.plugin = plugin;
        this.playerCollectionDAO = plugin.getPlayerCollectionDAO();
        this.allTags = initializeTags();
    }

    private List<Tag> initializeTags() {
        List<Tag> tags = new ArrayList<>();

        // Prefix tags
        tags.add(new Tag("§6Miner§r ", true, "ores"));
        tags.add(new Tag("§2Lumberjack§r ", true, "wood"));
        tags.add(new Tag("§cStoner§r ", true, "redstone"));
        tags.add(new Tag("§nGypsy§r ", true, "copper"));
        tags.add(new Tag("§2Environmentalist§r ", true, "nature"));
        tags.add(new Tag("§4Hunter§r ", true, "mob drops"));
        tags.add(new Tag("§7Platinum Artist§r ", true, "music discs"));
        tags.add(new Tag("§4D§ce§6c§eo§2r§aa§bt§9o§dr§5", true, "terracotta"));
        tags.add(new Tag("§bShatterproof§r ", true, "glass"));
        tags.add(new Tag("§6Shepherd§r ", true, "wool"));

        // Suffix tags
        tags.add(new Tag(" §bis Collecting§r", false, null));
        tags.add(new Tag(" §jis Handy§r", false, "utilities"));
        tags.add(new Tag(" §dIs getting head§r", false, "heads"));
        tags.add(new Tag(" §0is Dying§r", false, "dyes"));
        tags.add(new Tag(" §4Got Banned§r", false, "banners"));
        tags.add(new Tag(" §7The Armourer§r", false, "armour"));
        tags.add(new Tag(" §7The Tool§r", false, "weapons and tools"));

        return tags;
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
