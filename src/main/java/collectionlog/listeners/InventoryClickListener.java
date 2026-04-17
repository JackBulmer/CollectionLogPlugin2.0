package collectionlog.listeners;

import collectionlog.CollectionLogPlugin;
import collectionlog.managers.ProgressTrackingManager;
import collectionlog.utils.CollectionMatcher;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;

/**
 * Tracks machine output interactions (furnace, brewing, smithing).
 */
public class InventoryClickListener implements Listener {
    private static final int BREWING_FIRST_BOTTLE_SLOT = 0;
    private static final int BREWING_LAST_BOTTLE_SLOT = 2;

    private final CollectionLogPlugin plugin;
    private final CollectionMatcher collectionMatcher;
    private final ProgressTrackingManager progressTrackingManager;

    public InventoryClickListener(CollectionLogPlugin plugin) {
        this.plugin = plugin;
        this.collectionMatcher = plugin.getCollectionMatcher();
        this.progressTrackingManager = plugin.getProgressTrackingManager();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || collectionMatcher == null
                || progressTrackingManager == null
                || !isTakeAction(event.getAction())) {
            return;
        }

        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlot() < 0 || event.getRawSlot() >= topSize) {
            return;
        }

        InventoryType inventoryType = event.getView().getTopInventory().getType();
        if (!isRelevantSlot(event, inventoryType)) {
            return;
        }

        ItemStack currentItem = event.getCurrentItem();
        if (currentItem == null || currentItem.getType().isAir()) {
            return;
        }

        for (CollectionMatcher.CollectionMatch match : collectionMatcher.findMatches(currentItem)) {
            progressTrackingManager.trackItemCollection(player, match.collection(), match.signature())
                    .exceptionally(throwable -> {
                        plugin.getLogger().severe("Failed to track machine-output progress: " + throwable.getMessage());
                        return null;
                    });
        }
    }

    private boolean isRelevantSlot(InventoryClickEvent event, InventoryType inventoryType) {
        return switch (inventoryType) {
            case FURNACE, BLAST_FURNACE, SMOKER ->
                    event.getSlotType() == InventoryType.SlotType.RESULT || event.getSlot() == 2;
            // Brewing stand bottle output slots are 0-2.
            case BREWING -> event.getSlot() >= BREWING_FIRST_BOTTLE_SLOT
                    && event.getSlot() <= BREWING_LAST_BOTTLE_SLOT;
            case SMITHING -> event.getSlotType() == InventoryType.SlotType.RESULT;
            default -> false;
        };
    }

    private boolean isTakeAction(InventoryAction action) {
        return switch (action) {
            case PICKUP_ALL,
                 PICKUP_HALF,
                 PICKUP_ONE,
                 PICKUP_SOME,
                 MOVE_TO_OTHER_INVENTORY,
                 HOTBAR_MOVE_AND_READD,
                 HOTBAR_SWAP,
                 COLLECT_TO_CURSOR -> true;
            default -> false;
        };
    }
}
