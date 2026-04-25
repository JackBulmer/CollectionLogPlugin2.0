package collectionlog.listeners;

import collectionlog.CollectionLogPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * Listener for entity death events.
 * NOTE: This no longer tracks mob drops automatically.
 * Items are only tracked when the player picks them up (see ItemPickupListener).
 */
public class EntityDeathListener implements Listener {
    private final CollectionLogPlugin plugin;

    public EntityDeathListener(CollectionLogPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        // Entity death is now handled by MobHeadDropListener for head/egg drops.
        // Item collection is tracked when the player picks up items (ItemPickupListener).
        // This listener is kept for potential future mob-death-specific features.
    }
}
