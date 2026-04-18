package collectionlog.utils;

import collectionlog.collections.CollectionDefinition;
import collectionlog.collections.SubCollectionDefinition;
import collectionlog.models.PlayerCollectionProgress;
import collectionlog.models.ProgressResult;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

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
                "§6✦ §eItem Collection Updated: §f%s §dProgress: %.2f%% (%d/%d)",
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
            double subPercentage = (subCollectedCount * 100.0) / subTotalCount;

            String subMessage = String.format(
                    "§6✦ §eItem Collection Updated: §f%s §dProgress: %.2f%% (%d/%d)",
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
        // Access the PlayerCollectionDAO through the plugin
        try {
            java.lang.reflect.Method method = plugin.getClass().getMethod("getPlayerCollectionDAO");
            Object dao = method.invoke(plugin);

            // Get collected items
            method = dao.getClass().getMethod("getCollectedItems", java.util.UUID.class, String.class);
            @SuppressWarnings("unchecked")
            java.util.concurrent.CompletableFuture<java.util.List<String>> future =
                    (java.util.concurrent.CompletableFuture<java.util.List<String>>) method.invoke(dao, player.getUniqueId(), collection.getId());

            java.util.List<String> collectedItems = future.join();
            java.util.Set<String> collectedSet = new java.util.HashSet<>(collectedItems);

            // Count how many sub-collection items are collected
            int count = 0;
            for (String item : subCollection.getItems()) {
                if (collectedSet.contains(item)) {
                    count++;
                }
            }
            return count;

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get sub-collection progress: " + e.getMessage());
            return 0;
        }
    }
}
