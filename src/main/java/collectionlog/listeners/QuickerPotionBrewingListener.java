package collectionlog.listeners;

import collectionlog.CollectionLogPlugin;
import collectionlog.database.dao.BlockOwnershipDAO;
import collectionlog.perks.PerkManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BrewingStand;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Implements the quicker_potion_brewing perk.
 * Players with this perk brew potions twice as fast.
 */
public class QuickerPotionBrewingListener implements Listener {

    private static final String PERK_ID = "quicker_potion_brewing";
    private static final int EXTRA_BREW_TIME_REDUCTION_PER_TICK = 1;

    private final CollectionLogPlugin plugin;
    private final PerkManager perkManager;
    private final BlockOwnershipDAO blockOwnershipDAO;

    private final Map<Location, UUID> activeBrewingStands = new HashMap<>();
    private final Map<Location, UUID> manualBrewers = new HashMap<>();

    private BukkitTask speedTask;

    public QuickerPotionBrewingListener(CollectionLogPlugin plugin) {
        this.plugin = plugin;
        this.perkManager = plugin.getPerkManager();
        this.blockOwnershipDAO = plugin.getBlockOwnershipDAO();
        startSpeedTask();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getInventory() instanceof BrewerInventory)) {
            return;
        }

        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        Location location = normalizeLocation(event.getInventory().getLocation());
        if (location == null) {
            return;
        }

        manualBrewers.put(location, player.getUniqueId());

        if (isBrewing(location)) {
            return;
        }

        if (perkManager.hasUnlocked(player.getUniqueId(), PERK_ID)) {
            activeBrewingStands.put(location, player.getUniqueId());
        } else {
            activeBrewingStands.remove(location);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getType() != InventoryType.BREWING) {
            return;
        }

        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        Location location = normalizeLocation(event.getInventory().getLocation());
        if (location == null) {
            return;
        }

        UUID remainingViewer = findRemainingViewer(event, player.getUniqueId());
        if (remainingViewer != null) {
            manualBrewers.put(location, remainingViewer);
        } else {
            manualBrewers.remove(location);
        }

        if (!isBrewing(location)) {
            refreshIdleTracking(location);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        if (!(event.getDestination() instanceof BrewerInventory)) {
            return;
        }

        Location location = normalizeLocation(event.getDestination().getLocation());
        if (location == null || manualBrewers.containsKey(location)) {
            return;
        }

        UUID owner = blockOwnershipDAO.getOwner(location);
        if (owner != null && perkManager.hasUnlocked(owner, PERK_ID)) {
            activeBrewingStands.put(location, owner);
        } else if (!isBrewing(location)) {
            activeBrewingStands.remove(location);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBrew(BrewEvent event) {
        Location location = normalizeLocation(event.getBlock().getLocation());
        if (location != null) {
            refreshIdleTracking(location);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.BREWING_STAND) {
            return;
        }

        Location location = normalizeLocation(event.getBlock().getLocation());
        if (location == null) {
            return;
        }

        activeBrewingStands.remove(location);
        manualBrewers.remove(location);
    }

    public void shutdown() {
        if (speedTask != null) {
            speedTask.cancel();
            speedTask = null;
        }
        activeBrewingStands.clear();
        manualBrewers.clear();
    }

    private void startSpeedTask() {
        speedTask = new BukkitRunnable() {
            @Override
            public void run() {
                Iterator<Map.Entry<Location, UUID>> iterator = activeBrewingStands.entrySet().iterator();

                while (iterator.hasNext()) {
                    Map.Entry<Location, UUID> entry = iterator.next();
                    Location location = entry.getKey();
                    UUID playerId = entry.getValue();

                    Block block = location.getBlock();
                    if (block.getType() != Material.BREWING_STAND) {
                        manualBrewers.remove(location);
                        iterator.remove();
                        continue;
                    }

                    if (!perkManager.hasUnlocked(playerId, PERK_ID)) {
                        iterator.remove();
                        continue;
                    }

                    BrewingStand brewingStand = (BrewingStand) block.getState();
                    if (brewingStand.getBrewingTime() > 0) {
                        brewingStand.setBrewingTime(Math.max(0,
                                brewingStand.getBrewingTime() - EXTRA_BREW_TIME_REDUCTION_PER_TICK));
                        brewingStand.update();
                        continue;
                    }

                    UUID updatedSource = resolveIdleSource(location);
                    if (updatedSource == null) {
                        iterator.remove();
                    } else if (!updatedSource.equals(playerId)) {
                        entry.setValue(updatedSource);
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void refreshIdleTracking(Location location) {
        UUID updatedSource = resolveIdleSource(location);
        if (updatedSource == null) {
            activeBrewingStands.remove(location);
        } else {
            activeBrewingStands.put(location, updatedSource);
        }
    }

    private UUID resolveIdleSource(Location location) {
        UUID manualBrewer = manualBrewers.get(location);
        if (manualBrewer != null) {
            return perkManager.hasUnlocked(manualBrewer, PERK_ID) ? manualBrewer : null;
        }

        UUID owner = blockOwnershipDAO.getOwner(location);
        return owner != null && perkManager.hasUnlocked(owner, PERK_ID) ? owner : null;
    }

    private UUID findRemainingViewer(InventoryCloseEvent event, UUID closingPlayerId) {
        for (HumanEntity viewer : event.getInventory().getViewers()) {
            if (viewer instanceof Player player && !player.getUniqueId().equals(closingPlayerId)) {
                return player.getUniqueId();
            }
        }
        return null;
    }

    private boolean isBrewing(Location location) {
        Block block = location.getBlock();
        if (block.getType() != Material.BREWING_STAND) {
            return false;
        }

        BrewingStand brewingStand = (BrewingStand) block.getState();
        return brewingStand.getBrewingTime() > 0;
    }

    private Location normalizeLocation(Location location) {
        return location == null ? null : location.getBlock().getLocation();
    }
}
