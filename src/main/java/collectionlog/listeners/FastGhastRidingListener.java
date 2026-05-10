package collectionlog.listeners;

import collectionlog.CollectionLogPlugin;
import collectionlog.perks.PerkManager;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Applies the fast_ghast_riding perk by boosting Happy Ghast flying speed
 * when the controlling rider has the perk unlocked.
 */
public class FastGhastRidingListener implements Listener {

    private static final String PERK_ID = "fast_ghast_riding";
    private static final double SPEED_MULTIPLIER = 2.0;

    private final CollectionLogPlugin plugin;
    private final PerkManager perkManager;
    private final Map<UUID, Double> originalSpeeds = new HashMap<>();

    public FastGhastRidingListener(CollectionLogPlugin plugin) {
        this.plugin = plugin;
        this.perkManager = plugin.getPerkManager();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityMount(EntityMountEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Entity mount = event.getMount();
        if (!isHappyGhast(mount)) {
            return;
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (mount.isValid() && !mount.isDead()) {
                checkAndApplySpeedBoost((LivingEntity) mount);
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Entity dismounted = event.getDismounted();
        if (!isHappyGhast(dismounted)) {
            return;
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (dismounted.isValid() && !dismounted.isDead()) {
                if (dismounted.getPassengers().isEmpty()) {
                    resetSpeed((LivingEntity) dismounted);
                } else {
                    checkAndApplySpeedBoost((LivingEntity) dismounted);
                }
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        checkVehicleAfterPlayerExit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        checkVehicleAfterPlayerExit(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        Entity vehicle = player.getVehicle();

        if (vehicle != null && isHappyGhast(vehicle)) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (vehicle.isValid() && !vehicle.isDead()) {
                    checkAndApplySpeedBoost((LivingEntity) vehicle);
                }
            }, 1L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        if (isHappyGhast(event.getEntity())) {
            originalSpeeds.remove(event.getEntity().getUniqueId());
        }
    }

    private void checkVehicleAfterPlayerExit(Player player) {
        Entity vehicle = player.getVehicle();
        if (vehicle == null || !isHappyGhast(vehicle)) {
            return;
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (vehicle.isValid() && !vehicle.isDead()) {
                if (vehicle.getPassengers().isEmpty()) {
                    resetSpeed((LivingEntity) vehicle);
                } else {
                    checkAndApplySpeedBoost((LivingEntity) vehicle);
                }
            }
        }, 1L);
    }

    private void checkAndApplySpeedBoost(LivingEntity happyGhast) {
        List<Entity> passengers = happyGhast.getPassengers();
        if (passengers.isEmpty()) {
            resetSpeed(happyGhast);
            return;
        }

        Entity controller = passengers.get(0);
        if (!(controller instanceof Player player)) {
            resetSpeed(happyGhast);
            return;
        }

        if (perkManager.hasUnlocked(player.getUniqueId(), PERK_ID)) {
            applySpeedBoost(happyGhast);
        } else {
            resetSpeed(happyGhast);
        }
    }

    private void applySpeedBoost(LivingEntity happyGhast) {
        AttributeInstance flyingSpeed = happyGhast.getAttribute(Attribute.FLYING_SPEED);
        if (flyingSpeed == null) {
            return;
        }

        UUID ghastId = happyGhast.getUniqueId();
        originalSpeeds.putIfAbsent(ghastId, flyingSpeed.getBaseValue());

        double boostedSpeed = originalSpeeds.get(ghastId) * SPEED_MULTIPLIER;
        if (Math.abs(flyingSpeed.getBaseValue() - boostedSpeed) > 0.0001D) {
            flyingSpeed.setBaseValue(boostedSpeed);
        }
    }

    private void resetSpeed(LivingEntity happyGhast) {
        Double originalSpeed = originalSpeeds.remove(happyGhast.getUniqueId());
        if (originalSpeed == null) {
            return;
        }

        AttributeInstance flyingSpeed = happyGhast.getAttribute(Attribute.FLYING_SPEED);
        if (flyingSpeed != null) {
            flyingSpeed.setBaseValue(originalSpeed);
        }
    }

    private boolean isHappyGhast(Entity entity) {
        try {
            return entity.getType() == EntityType.HAPPY_GHAST;
        } catch (NoSuchFieldError ignored) {
            return "HAPPY_GHAST".equalsIgnoreCase(entity.getType().name());
        }
    }
}
