package collectionlog.utils;

import collectionlog.CollectionLogPlugin;
import collectionlog.collections.CollectionDefinition;
import collectionlog.collections.SubCollectionDefinition;
import collectionlog.database.dao.PlayerCollectionDAO;
import collectionlog.models.PlayerCollectionProgress;
import collectionlog.models.ProgressResult;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Global helper for progress-related chat messaging.
 */
public final class ProgressMessageHelper {
    private ProgressMessageHelper() {
    }

    /**
     * Central global method for sending progress messages.
     */
    public static void sendProgressMessage(Plugin plugin, Player player, ProgressResult result) {
        if (player == null || result == null || !result.hasProgress()) {
            return;
        }

        CollectionDefinition collection = result.getCollection();
        PlayerCollectionProgress progress = result.getProgress();
        if (collection == null || progress == null) {
            return;
        }

        // Main collection progress message
        double percentage = progress.getPercentage();
        String message = String.format(
                "§6✦ §aItem Collection Updated: §b%s §dProgress: %.2f%% (%d/%d)",
                collection.getName(),
                percentage,
                progress.getCollectedItems(),
                progress.getTotalItems()
        );
        player.sendMessage(message);

        // Sub-collection progress message (if item belongs to a sub-collection)
        SubCollectionDefinition subCollection = result.getSubCollection();
        if (subCollection != null) {
            // Calculate sub-collection progress
            int subCollectedCount = getSubCollectionProgress(plugin, player, collection, subCollection);
            int subTotalCount = subCollection.getTotalItems();
            double subPercentage = subTotalCount <= 0 ? 0.0 : (subCollectedCount * 100.0) / subTotalCount;

            String subMessage = String.format(
                    "§6✦ §aItem Collection Updated: §b%s §dProgress: %.2f%% (%d/%d)",
                    subCollection.getName(),
                    subPercentage,
                    subCollectedCount,
                    subTotalCount
            );
            player.sendMessage(subMessage);

            // Show completion message if just completed
            if (result.completedSubCollection()) {
                player.sendMessage(ChatColor.DARK_AQUA + "[CollectionLog] " + ChatColor.GOLD
                        + "Sub-Collection completed: " + ChatColor.YELLOW + subCollection.getName());
            }
        }

        // Collection completion broadcast
        if (result.completedCollection()) {
            String completionMessage = ChatColor.GOLD + "[CollectionLog] " + ChatColor.YELLOW + player.getName()
                    + ChatColor.GOLD + " completed the " + ChatColor.AQUA + collection.getName()
                    + ChatColor.GOLD + " collection!";
            Bukkit.getServer().broadcastMessage(completionMessage);

            if (plugin.getConfig().getBoolean("plugin.debug", false)) {
                plugin.getLogger().info("Broadcasted collection completion for "
                        + player.getName() + " -> " + collection.getId());
            }
        }
    }

    /**
     * Count how many items from a sub-collection the player has collected
     */
    private static int getSubCollectionProgress(Plugin plugin, Player player, CollectionDefinition collection, SubCollectionDefinition subCollection) {
        if (!(plugin instanceof CollectionLogPlugin collectionLogPlugin)) {
            return 0;
        }

        PlayerCollectionDAO playerCollectionDAO = collectionLogPlugin.getPlayerCollectionDAO();
        if (playerCollectionDAO == null) {
            return 0;
        }

        try {
            List<String> collectedItems = playerCollectionDAO
                    .getCollectedItems(player.getUniqueId(), collection.getId())
                    .join();
            Set<String> collectedSet = new HashSet<>(collectedItems);

            int count = 0;
            for (String item : subCollection.getItems()) {
                if (collectedSet.contains(item)) {
                    count++;
                }
            }
            return count;
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Failed to get sub-collection progress: " + e.getMessage());
            return 0;
        }
    }
}
