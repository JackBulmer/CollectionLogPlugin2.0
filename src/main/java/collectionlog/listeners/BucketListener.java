package collectionlog.listeners;

import collectionlog.CollectionLogPlugin;
import collectionlog.managers.ProgressTrackingManager;
import collectionlog.utils.CollectionMatcher;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Tracks bucket collection when players fill buckets.
 * Handles: water, lava, milk, powder snow, and entity buckets (cod, salmon, tropical fish, axolotl, tadpole).
 */
public class BucketListener implements Listener {
    private final CollectionLogPlugin plugin;
    private final CollectionMatcher collectionMatcher;
    private final ProgressTrackingManager progressTrackingManager;

    public BucketListener(CollectionLogPlugin plugin) {
        this.plugin = plugin;
        this.collectionMatcher = plugin.getCollectionMatcher();
        this.progressTrackingManager = plugin.getProgressTrackingManager();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Player player = event.getPlayer();
        ItemStack bucket = event.getItemStack();

        if (bucket == null || bucket.getType().isAir()) {
            return;
        }

        if (collectionMatcher == null || progressTrackingManager == null) {
            return;
        }

        for (CollectionMatcher.CollectionMatch match : collectionMatcher.findMatches(bucket, null)) {
            progressTrackingManager.trackItemCollection(player, match.collection(), match.signature())
                    .exceptionally(throwable -> {
                        plugin.getLogger().severe("Failed to track bucket fill: " + throwable.getMessage());
                        return null;
                    });
        }
    }
}
