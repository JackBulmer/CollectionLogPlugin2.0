package collectionlog.listeners;

import collectionlog.CollectionLogPlugin;
import collectionlog.anticheat.ItemTrackingManager;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Tracks items dropped from player inventories.
 * Prevents other players from getting collection credit when picking them up.
 */
public class PlayerDropItemListener implements Listener {
    private static final double DEATH_DROP_SEARCH_RADIUS = 2.5;

    private final CollectionLogPlugin plugin;
    private final ItemTrackingManager itemTrackingManager;

    public PlayerDropItemListener(CollectionLogPlugin plugin) {
        this.plugin = plugin;
        this.itemTrackingManager = plugin.getItemTrackingManager();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();

        // Track this item as belonging to the player who dropped it
        itemTrackingManager.trackItem(event.getItemDrop(), player.getUniqueId());

        if (plugin.getConfig().getBoolean("plugin.debug", false)) {
            plugin.getLogger().info(player.getName() + " dropped "
                    + event.getItemDrop().getItemStack().getType() + " x"
                    + event.getItemDrop().getItemStack().getAmount());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (event.getDrops().isEmpty()) {
            return;
        }

        Player player = event.getEntity();
        Location deathLocation = player.getLocation();

        // Delay one tick to capture spawned item entities from death drops
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    deathLocation.getWorld().getNearbyEntities(
                            deathLocation,
                            DEATH_DROP_SEARCH_RADIUS,
                            DEATH_DROP_SEARCH_RADIUS,
                            DEATH_DROP_SEARCH_RADIUS
                    ).forEach(entity -> {
                        if (entity instanceof Item itemEntity && !itemEntity.isDead()) {
                            itemTrackingManager.trackItem(itemEntity, player.getUniqueId());
                        }
                    });

                    if (plugin.getConfig().getBoolean("plugin.debug", false)) {
                        plugin.getLogger().info("Tracked death drops for " + player.getName());
                    }
                } catch (Exception e) {
                    plugin.getLogger().severe("Failed to track player death drops: " + e.getMessage());
                }
            }
        }.runTaskLater(plugin, 1L);
    }
}
