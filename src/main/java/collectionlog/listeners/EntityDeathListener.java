package collectionlog.listeners;

import collectionlog.CollectionLogPlugin;
import collectionlog.managers.ProgressTrackingManager;
import collectionlog.utils.CollectionMatcher;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Tracks item collection from mob kills.
 */
public class EntityDeathListener implements Listener {
    private final CollectionLogPlugin plugin;
    private final CollectionMatcher collectionMatcher;
    private final ProgressTrackingManager progressTrackingManager;

    public EntityDeathListener(CollectionLogPlugin plugin) {
        this.plugin = plugin;
        this.collectionMatcher = plugin.getCollectionMatcher();
        this.progressTrackingManager = plugin.getProgressTrackingManager();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || collectionMatcher == null || progressTrackingManager == null) {
            return;
        }

        for (ItemStack drop : event.getDrops()) {
            if (drop == null || drop.getType().isAir()) {
                continue;
            }

            for (CollectionMatcher.CollectionMatch match : collectionMatcher.findMatches(drop, event.getEntityType())) {
                progressTrackingManager.trackItemCollection(killer, match.collection(), match.signature())
                        .exceptionally(throwable -> {
                            plugin.getLogger().severe("Failed to track mob-drop progress: " + throwable.getMessage());
                            return null;
                        });
            }
        }
    }
}
