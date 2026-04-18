package collectionlog.managers;

import collectionlog.CollectionLogPlugin;
import collectionlog.collections.CollectionDefinition;
import collectionlog.collections.SubCollectionDefinition;
import collectionlog.database.AsyncTaskManager;
import collectionlog.database.dao.PlayerCollectionDAO;
import collectionlog.models.PlayerCollectionProgress;
import collectionlog.models.ProgressResult;
import collectionlog.utils.ProgressMessageHelper;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Handles player collection progress tracking logic.
 */
public class ProgressTrackingManager {
    private final Plugin plugin;
    private final PlayerCollectionDAO playerCollectionDAO;
    private final AsyncTaskManager asyncTaskManager;

    public ProgressTrackingManager(Plugin plugin, PlayerCollectionDAO playerCollectionDAO, AsyncTaskManager asyncTaskManager) {
        this.plugin = plugin;
        this.playerCollectionDAO = playerCollectionDAO;
        this.asyncTaskManager = asyncTaskManager;
    }

    /**
     * Track a newly collected item.
     */
    public CompletableFuture<ProgressResult> trackItemCollection(UUID playerUUID, CollectionDefinition collection, String itemSignature) {
        if (playerUUID == null || collection == null || itemSignature == null || itemSignature.isBlank()) {
            return CompletableFuture.completedFuture(ProgressResult.noProgress());
        }

        String collectionId = collection.getId();

        return playerCollectionDAO.hasCollectedItem(playerUUID, collectionId, itemSignature)
                .thenCompose(alreadyCollected -> {
                    if (alreadyCollected) {
                        return CompletableFuture.completedFuture(ProgressResult.noProgress());
                    }

                    return playerCollectionDAO.addCollectedItem(playerUUID, collectionId, itemSignature)
                            .thenCompose(added -> {
                                if (!added) {
                                    return CompletableFuture.completedFuture(ProgressResult.noProgress());
                                }

                                return playerCollectionDAO.initializeCollection(playerUUID, collectionId, collection.getTotalItems())
                                        .thenCompose(v1 -> playerCollectionDAO.updateProgress(playerUUID, collectionId))
                                        .thenCompose(v2 -> playerCollectionDAO.getProgress(playerUUID, collectionId))
                                        .thenCompose(progress -> finalizeProgress(playerUUID, collection, itemSignature, progress));
                            });
                });
    }

    /**
     * Track item collection and send global progress messages if there is progress.
     */
    public CompletableFuture<ProgressResult> trackItemCollection(Player player, CollectionDefinition collection, String itemSignature) {
        if (player == null) {
            return CompletableFuture.completedFuture(ProgressResult.noProgress());
        }

        return trackItemCollection(player.getUniqueId(), collection, itemSignature)
                .thenApply(result -> {
                    if (result.hasProgress()) {
                        asyncTaskManager.runSync(() -> {
                            ProgressMessageHelper.sendProgressMessage(plugin, player, result);
                            triggerMilestoneChecks(player, result);
                        });
                    }
                    return result;
                });
    }

    private CompletableFuture<ProgressResult> finalizeProgress(
            UUID playerUUID,
            CollectionDefinition collection,
            String itemSignature,
            PlayerCollectionProgress progress
    ) {
        if (progress == null) {
            return CompletableFuture.completedFuture(ProgressResult.noProgress());
        }

        CompletableFuture<CompletionState> completionStateFuture;
        if (progress.isComplete() && progress.getCompletionDate() <= 0) {
            completionStateFuture = playerCollectionDAO.markCollectionComplete(playerUUID, collection.getId())
                    .thenCompose(v -> playerCollectionDAO.getProgress(playerUUID, collection.getId()))
                    .thenApply(updated -> new CompletionState(updated != null ? updated : progress, true));
        } else {
            completionStateFuture = CompletableFuture.completedFuture(new CompletionState(progress, false));
        }

        SubCollectionDefinition subCollection = findSubCollection(collection, itemSignature);
        CompletableFuture<Boolean> subCollectionCompletedFuture;
        if (subCollection == null) {
            subCollectionCompletedFuture = CompletableFuture.completedFuture(false);
        } else {
            subCollectionCompletedFuture = isSubCollectionComplete(playerUUID, collection.getId(), subCollection);
        }

        return completionStateFuture.thenCombine(subCollectionCompletedFuture, (state, completedSubCollection) ->
                        new ProgressResult(
                                true,
                                state.collectionJustCompleted(),
                                completedSubCollection,
                                collection,
                                subCollection,
                                state.progress()
                        )
                );
    }

    private SubCollectionDefinition findSubCollection(CollectionDefinition collection, String itemSignature) {
        for (SubCollectionDefinition subCollection : collection.getSubCollections()) {
            if (subCollection.getItems().contains(itemSignature)) {
                return subCollection;
            }
        }
        return null;
    }

    private CompletableFuture<Boolean> isSubCollectionComplete(UUID playerUUID, String collectionId, SubCollectionDefinition subCollection) {
        return playerCollectionDAO.getCollectedItems(playerUUID, collectionId)
                .thenApply(collectedItems -> {
                    Set<String> collected = new HashSet<>(collectedItems);
                    List<String> required = subCollection.getItems();
                    return !required.isEmpty() && collected.containsAll(required);
                });
    }

    private void triggerMilestoneChecks(Player player, ProgressResult result) {
        if (!(plugin instanceof CollectionLogPlugin collectionLogPlugin) || collectionLogPlugin.getMilestoneManager() == null) {
            return;
        }

        if (result.completedCollection() && result.getCollection() != null) {
            collectionLogPlugin.getMilestoneManager().checkCollectionCompletion(
                    player.getUniqueId(),
                    result.getCollection().getId()
            );
        }

        if (result.completedSubCollection() && result.getCollection() != null && result.getSubCollection() != null) {
            collectionLogPlugin.getMilestoneManager().checkSubCollectionCompletion(
                    player.getUniqueId(),
                    result.getCollection().getId(),
                    result.getSubCollection().getName()
            );
        }
    }

    private record CompletionState(PlayerCollectionProgress progress, boolean collectionJustCompleted) {
    }
}
