package collectionlog.listeners;

import collectionlog.CollectionLogPlugin;
import collectionlog.managers.ProgressTrackingManager;
import collectionlog.utils.CollectionMatcher;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBucketEntityEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Tracks bucket collection when players fill buckets.
 * Handles: water, lava, milk, powder snow, and entity buckets (cod, salmon, tropical fish, pufferfish, axolotl, tadpole).
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

    /**
     * Handles regular bucket fills: water, lava, powder snow, milk
     */
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

    /**
     * Handles entity bucket fills: cod, salmon, tropical fish, pufferfish, axolotl, tadpole
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEntity(PlayerBucketEntityEvent event) {
        Player player = event.getPlayer();
        EntityType entityType = event.getEntity().getType();

        // Map entity type to bucket material
        Material bucketMaterial = getEntityBucketMaterial(entityType);
        if (bucketMaterial == null) {
            return;
        }

        if (collectionMatcher == null || progressTrackingManager == null) {
            return;
        }

        ItemStack bucket = new ItemStack(bucketMaterial);

        for (CollectionMatcher.CollectionMatch match : collectionMatcher.findMatches(bucket, null)) {
            progressTrackingManager.trackItemCollection(player, match.collection(), match.signature())
                    .exceptionally(throwable -> {
                        plugin.getLogger().severe("Failed to track entity bucket: " + throwable.getMessage());
                        return null;
                    });
        }
    }

    /**
     * Map entity types to their corresponding bucket materials
     */
    private Material getEntityBucketMaterial(EntityType entityType) {
        return switch (entityType) {
            case COD -> Material.COD_BUCKET;
            case SALMON -> Material.SALMON_BUCKET;
            case TROPICAL_FISH -> Material.TROPICAL_FISH_BUCKET;
            case PUFFERFISH -> Material.PUFFERFISH_BUCKET;
            case AXOLOTL -> Material.AXOLOTL_BUCKET;
            case TADPOLE -> Material.TADPOLE_BUCKET;
            default -> null;
        };
    }
}
