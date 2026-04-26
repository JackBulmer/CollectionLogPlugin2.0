package collectionlog.perks;

import collectionlog.CollectionLogPlugin;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;

/**
 * Cancels natural phantom spawns near any player who has the
 * {@code toggle_phantoms} perk unlocked and enabled.
 */
public class PhantomTogglePerk implements Listener {

    private final PerkManager perkManager;

    public PhantomTogglePerk(CollectionLogPlugin plugin) {
        this.perkManager = plugin.getPerkManager();
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPhantomSpawn(CreatureSpawnEvent event) {
        if (event.getEntityType() != EntityType.PHANTOM) return;
        if (event.getSpawnReason() != SpawnReason.NATURAL) return;

        Location loc = event.getLocation();
        for (Player player : loc.getNearbyPlayers(64)) {
            if (perkManager.isActive(player, "toggle_phantoms")) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
