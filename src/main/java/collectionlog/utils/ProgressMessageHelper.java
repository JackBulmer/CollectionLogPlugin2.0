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

        double percentage = progress.getPercentage();
        String message = String.format(
                "§6✦ §eItem Collection Updated: §f%s §dProgress: %.2f%% (%d/%d)",
                collection.getName(),
                percentage,
                progress.getCollectedItems(),
                progress.getTotalItems()
        );
        player.sendMessage(message);

        if (result.completedSubCollection()) {
            SubCollectionDefinition sub = result.getSubCollection();
            if (sub != null) {
                player.sendMessage(ChatColor.DARK_AQUA + "[CollectionLog] " + ChatColor.GOLD
                        + "Sub-Collection completed: " + ChatColor.YELLOW + sub.getName());
            }
        }

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
}
