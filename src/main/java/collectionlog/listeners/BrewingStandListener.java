package collectionlog.listeners;

import collectionlog.CollectionLogPlugin;
import collectionlog.managers.ProgressTrackingManager;
import collectionlog.utils.CollectionMatcher;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

/**
 * Tracks potion collection when players retrieve potions from brewing stands.
 */
public class BrewingStandListener implements Listener {
    private final CollectionLogPlugin plugin;
    private final CollectionMatcher collectionMatcher;
    private final ProgressTrackingManager progressTrackingManager;

    public BrewingStandListener(CollectionLogPlugin plugin) {
        this.plugin = plugin;
        this.collectionMatcher = plugin.getCollectionMatcher();
        this.progressTrackingManager = plugin.getProgressTrackingManager();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrewingStandClick(InventoryClickEvent event) {
        // Only brewing stand inventory
        if (!(event.getInventory() instanceof BrewerInventory)) {
            return;
        }

        // Only player clicks
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // Slots 0-2 are the three potion bottle slots
        int slot = event.getRawSlot();
        if (slot < 0 || slot > 2) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }

        // Only track actual potions (not water bottles, mundane, thick, awkward)
        if (!isValidPotion(clicked)) {
            return;
        }

        if (collectionMatcher == null || progressTrackingManager == null) {
            return;
        }

        for (CollectionMatcher.CollectionMatch match : collectionMatcher.findMatches(clicked, null)) {
            progressTrackingManager.trackItemCollection(player, match.collection(), match.signature())
                    .exceptionally(throwable -> {
                        plugin.getLogger().severe("Failed to track brewing stand potion: " + throwable.getMessage());
                        return null;
                    });
        }
    }

    /**
     * Check if the item is a valid potion to track (not water bottle, mundane, thick, or awkward).
     */
    private boolean isValidPotion(ItemStack item) {
        Material type = item.getType();
        if (type != Material.POTION && type != Material.SPLASH_POTION && type != Material.LINGERING_POTION) {
            return false;
        }

        if (!(item.getItemMeta() instanceof PotionMeta meta)) {
            return false;
        }

        PotionType potionType = meta.getBasePotionType();
        return potionType != PotionType.WATER
                && potionType != PotionType.MUNDANE
                && potionType != PotionType.THICK
                && potionType != PotionType.AWKWARD;
    }
}
