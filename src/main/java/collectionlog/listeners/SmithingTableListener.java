package collectionlog.listeners;

import collectionlog.CollectionLogPlugin;
import collectionlog.managers.ProgressTrackingManager;
import collectionlog.utils.CollectionMatcher;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.SmithingInventory;
import org.bukkit.inventory.meta.ArmorMeta;

/**
 * Tracks item collection when players craft items at smithing tables.
 * Primarily used for netherite tool/armor upgrades.
 */
public class SmithingTableListener implements Listener {
    private final CollectionLogPlugin plugin;
    private final CollectionMatcher collectionMatcher;
    private final ProgressTrackingManager progressTrackingManager;

    public SmithingTableListener(CollectionLogPlugin plugin) {
        this.plugin = plugin;
        this.collectionMatcher = plugin.getCollectionMatcher();
        this.progressTrackingManager = plugin.getProgressTrackingManager();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSmithingTableClick(InventoryClickEvent event) {
        // Only smithing table inventory
        if (!(event.getInventory() instanceof SmithingInventory)) {
            return;
        }

        // Only player clicks
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // Result slot is slot 3 in smithing inventory (raw slot 3)
        if (event.getRawSlot() != 3) {
            return;
        }

        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType().isAir()) {
            return;
        }

        if (result.getItemMeta() instanceof ArmorMeta armorMeta && armorMeta.hasTrim()) {
            return;
        }

        if (collectionMatcher == null || progressTrackingManager == null) {
            return;
        }

        for (CollectionMatcher.CollectionMatch match : collectionMatcher.findMatches(result, null)) {
            progressTrackingManager.trackItemCollection(player, match.collection(), match.signature())
                    .exceptionally(throwable -> {
                        plugin.getLogger().severe("Failed to track smithing table craft: " + throwable.getMessage());
                        return null;
                    });
        }
    }
}
