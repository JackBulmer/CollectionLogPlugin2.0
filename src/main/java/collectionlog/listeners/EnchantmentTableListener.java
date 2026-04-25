package collectionlog.listeners;

import collectionlog.CollectionLogPlugin;
import collectionlog.managers.ProgressTrackingManager;
import collectionlog.utils.CollectionMatcher;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;

import java.util.Map;

/**
 * Tracks enchantment collection when players enchant items at enchanting tables.
 */
public class EnchantmentTableListener implements Listener {
    private final CollectionLogPlugin plugin;
    private final CollectionMatcher collectionMatcher;
    private final ProgressTrackingManager progressTrackingManager;

    public EnchantmentTableListener(CollectionLogPlugin plugin) {
        this.plugin = plugin;
        this.collectionMatcher = plugin.getCollectionMatcher();
        this.progressTrackingManager = plugin.getProgressTrackingManager();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchantItem(EnchantItemEvent event) {
        Player player = event.getEnchanter();
        Map<Enchantment, Integer> enchantments = event.getEnchantsToAdd();

        if (enchantments.isEmpty() || collectionMatcher == null || progressTrackingManager == null) {
            return;
        }

        for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
            String enchantKey = entry.getKey().getKey().getKey();
            int level = entry.getValue();

            for (CollectionMatcher.CollectionMatch match : collectionMatcher.findMatchesForEnchantment(enchantKey, level)) {
                progressTrackingManager.trackItemCollection(player, match.collection(), match.signature())
                        .exceptionally(throwable -> {
                            plugin.getLogger().severe("Failed to track enchantment: " + throwable.getMessage());
                            return null;
                        });
            }
        }
    }
}
