package collectionlog.utils;

import collectionlog.CollectionLogPlugin;
import collectionlog.managers.ProgressTrackingManager;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Utility for tracking collection progress from enchantments present on an item.
 */
public final class EnchantmentCollectionTracker {
    private EnchantmentCollectionTracker() {
    }

    public static void trackEnchantments(
            CollectionLogPlugin plugin,
            CollectionMatcher collectionMatcher,
            ProgressTrackingManager progressTrackingManager,
            Player player,
            ItemStack item,
            boolean includeStoredEnchantments,
            String source
    ) {
        if (plugin == null
                || collectionMatcher == null
                || progressTrackingManager == null
                || player == null
                || item == null
                || item.getType().isAir()) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        boolean hasDirectEnchantments = meta.hasEnchants();
        boolean hasStoredEnchantments = includeStoredEnchantments
                && meta instanceof EnchantmentStorageMeta storageMeta
                && storageMeta.hasStoredEnchants();
        if (!hasDirectEnchantments && !hasStoredEnchantments) {
            return;
        }

        Map<Enchantment, Integer> enchantments = new LinkedHashMap<>();
        if (hasDirectEnchantments) {
            enchantments.putAll(item.getEnchantments());
        }
        if (hasStoredEnchantments && meta instanceof EnchantmentStorageMeta storageMeta) {
            storageMeta.getStoredEnchants().forEach((enchantment, level) ->
                    enchantments.merge(enchantment, Math.max(1, level), Math::max));
        }

        for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
            String enchantmentKey = entry.getKey().getKey().getKey();
            int level = Math.max(1, entry.getValue());

            for (CollectionMatcher.CollectionMatch match : collectionMatcher.findMatchesForEnchantment(enchantmentKey, level)) {
                progressTrackingManager.trackItemCollection(player, match.collection(), match.signature())
                        .exceptionally(throwable -> {
                            plugin.getLogger().severe(
                                    "Failed to track " + source + " enchantment progress: " + throwable.getMessage());
                            return null;
                        });
            }
        }
    }
}
