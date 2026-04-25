package collectionlog.listeners;

import collectionlog.CollectionLogPlugin;
import collectionlog.managers.ProgressTrackingManager;
import collectionlog.utils.CollectionMatcher;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantInventory;

/**
 * Tracks item collection when players trade with villagers or wandering traders.
 */
public class VillagerTradingListener implements Listener {
    private final CollectionLogPlugin plugin;
    private final CollectionMatcher collectionMatcher;
    private final ProgressTrackingManager progressTrackingManager;

    public VillagerTradingListener(CollectionLogPlugin plugin) {
        this.plugin = plugin;
        this.collectionMatcher = plugin.getCollectionMatcher();
        this.progressTrackingManager = plugin.getProgressTrackingManager();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVillagerTrade(InventoryClickEvent event) {
        // Only merchant (villager/wandering trader) inventories
        if (!(event.getInventory() instanceof MerchantInventory)) {
            return;
        }

        // Only player clicks
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // Result slot is slot 2 in merchant inventory (raw slot 2)
        if (event.getRawSlot() != 2) {
            return;
        }

        // Only full pickups (complete the trade)
        if (event.getAction() != InventoryAction.PICKUP_ALL) {
            return;
        }

        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType().isAir()) {
            return;
        }

        if (collectionMatcher == null || progressTrackingManager == null) {
            return;
        }

        for (CollectionMatcher.CollectionMatch match : collectionMatcher.findMatches(result, null)) {
            progressTrackingManager.trackItemCollection(player, match.collection(), match.signature())
                    .exceptionally(throwable -> {
                        plugin.getLogger().severe("Failed to track villager trade: " + throwable.getMessage());
                        return null;
                    });
        }
    }
}
