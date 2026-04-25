package collectionlog.listeners;

import collectionlog.CollectionLogPlugin;
import collectionlog.tags.TagManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.UUID;

/**
 * Updates player display names on join to include their equipped tags.
 */
public class PlayerJoinListener implements Listener {
    private final CollectionLogPlugin plugin;
    private final TagManager tagManager;

    public PlayerJoinListener(CollectionLogPlugin plugin) {
        this.plugin = plugin;
        this.tagManager = plugin.getTagManager();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        tagManager.getPlayerPrefix(playerId).thenAccept(prefix ->
                tagManager.getPlayerSuffix(playerId).thenAccept(suffix -> {
                    String displayName = prefix + player.getName() + suffix;
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (player.isOnline()) {
                            player.setDisplayName(displayName);
                            player.setPlayerListName(displayName);
                        }
                    });
                })
        );
    }
}
