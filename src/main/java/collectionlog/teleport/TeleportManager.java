package collectionlog.teleport;

import collectionlog.CollectionLogPlugin;
import collectionlog.teleport.data.ActiveTeleport;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TeleportManager implements Listener {
    private final CollectionLogPlugin plugin;
    private final Map<UUID, ActiveTeleport> activeTeleports = new ConcurrentHashMap<>();

    public TeleportManager(CollectionLogPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean hasActiveTeleport(UUID playerId) {
        return activeTeleports.containsKey(playerId);
    }

    /**
     * Start a teleport countdown for a player.
     *
     * @param player          The player being teleported
     * @param destination     The target location
     * @param countdownSeconds Number of seconds to count down (0 = instant)
     * @param tpCost          XP levels to deduct on success
     * @param costPlayer      The player who pays XP (can be different from the one teleporting)
     */
    public void startTeleport(Player player, Location destination, int countdownSeconds, int tpCost, Player costPlayer) {
        if (activeTeleports.containsKey(player.getUniqueId())) {
            player.sendMessage("§cTeleport already in progress");
            return;
        }

        if (countdownSeconds <= 0) {
            if (tpCost > 0 && costPlayer != null) {
                costPlayer.setLevel(costPlayer.getLevel() - tpCost);
            }
            player.teleport(destination);
            player.sendActionBar("§aTeleported!");
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
            return;
        }

        Location startLoc = player.getLocation().clone();

        ActiveTeleport activeTeleport = new ActiveTeleport(
            player.getUniqueId(),
            startLoc,
            countdownSeconds,
            () -> {
                if (tpCost > 0 && costPlayer != null && costPlayer.isOnline()) {
                    costPlayer.setLevel(costPlayer.getLevel() - tpCost);
                }
                player.teleport(destination);
                player.sendActionBar("§aTeleported!");
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                activeTeleports.remove(player.getUniqueId());
            },
            (reason) -> {
                player.sendActionBar("§cTeleport cancelled!");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                activeTeleports.remove(player.getUniqueId());
            }
        );

        activeTeleports.put(player.getUniqueId(), activeTeleport);

        int taskId = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            ActiveTeleport tp = activeTeleports.get(player.getUniqueId());
            if (tp == null) return;

            // Check horizontal movement (compare squared distance to avoid sqrt)
            Location currentLoc = player.getLocation();
            double dx = currentLoc.getX() - tp.getStartLocation().getX();
            double dz = currentLoc.getZ() - tp.getStartLocation().getZ();
            if (dx * dx + dz * dz > 0.01) {
                cancelTeleport(player.getUniqueId(), "moved");
                return;
            }

            int remaining = tp.getRemainingSeconds();

            String color = remaining >= 4 ? "§e" : remaining >= 2 ? "§6" : "§c";
            player.sendActionBar(color + "Teleporting in " + remaining + "...");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DING, 1f, 1f);

            tp.decrementSeconds();

            if (tp.getRemainingSeconds() < 0) {
                activeTeleports.remove(player.getUniqueId());
                plugin.getServer().getScheduler().cancelTask(tp.getTaskId());
                tp.getOnComplete().run();
            }
        }, 0L, 20L);

        activeTeleport.setTaskId(taskId);
    }

    public void cancelTeleport(UUID playerId, String reason) {
        ActiveTeleport tp = activeTeleports.remove(playerId);
        if (tp != null) {
            plugin.getServer().getScheduler().cancelTask(tp.getTaskId());
            tp.getOnCancel().accept(reason);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        cancelTeleport(event.getPlayer().getUniqueId(), "quit");
    }
}
