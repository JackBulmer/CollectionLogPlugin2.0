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

        // Pre-load perk toggle state so perk checks on the main thread are fast
        plugin.getPerkManager().loadPlayerToggles(playerId);

        // Apply permanent potion effects for all active potion-effect perks
        plugin.getPotionEffectManager().applyPermanentEffects(player);

        // Apply extra hearts based on completed extra_heart perk milestones
        plugin.getExtraHeartsManager().applyExtraHearts(player);

        // Sync custom recipe discovery with completed collections
        if (plugin.getCustomRecipeManager() != null) {
            plugin.getCustomRecipeManager().discoverRecipesForPlayer(player);
        }

        // Load and apply saved glowing preferences
        if (plugin.getGlowingManager() != null) {
            plugin.getGlowingManager().loadPlayerData(playerId);
        }

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
