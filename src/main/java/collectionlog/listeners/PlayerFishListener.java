package collectionlog.listeners;

import collectionlog.CollectionLogPlugin;
import collectionlog.managers.ProgressTrackingManager;
import collectionlog.utils.CollectionMatcher;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Tracks fish catches for collection progress.
 */
public class PlayerFishListener implements Listener {
    private final CollectionLogPlugin plugin;
    private final CollectionMatcher collectionMatcher;
    private final ProgressTrackingManager progressTrackingManager;

    public PlayerFishListener(CollectionLogPlugin plugin) {
        this.plugin = plugin;
        this.collectionMatcher = plugin.getCollectionMatcher();
        this.progressTrackingManager = plugin.getProgressTrackingManager();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH
                || !(event.getCaught() instanceof Item caughtItem)
                || collectionMatcher == null
                || progressTrackingManager == null) {
            return;
        }

        ItemStack itemStack = caughtItem.getItemStack();
        for (CollectionMatcher.CollectionMatch match : collectionMatcher.findMatches(itemStack)) {
            progressTrackingManager.trackItemCollection(event.getPlayer(), match.collection(), match.signature())
                    .exceptionally(throwable -> {
                        plugin.getLogger().severe("Failed to track fishing progress: " + throwable.getMessage());
                        return null;
                    });
        }
    }
}
