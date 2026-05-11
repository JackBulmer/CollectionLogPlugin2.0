package collectionlog.listeners;

import collectionlog.CollectionLogPlugin;
import collectionlog.managers.ProgressTrackingManager;
import collectionlog.utils.CollectionMatcher;
import collectionlog.utils.EnchantmentCollectionTracker;
import collectionlog.utils.ItemSignatureHelper;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Tracks items collected from naturally generated containers using Persistent Data Containers (PDC).
 *
 * <p>Two-level tracking:</p>
 * <ul>
 *   <li>Level 1: Permanently marks player-placed block/entity containers with a PDC tag on place.</li>
 *   <li>Level 2: Temporarily marks items placed by players in natural containers with a PDC tag.</li>
 * </ul>
 * <p>When a player takes an item from a container, both levels are checked before granting
 * collection credit. Only natural items (no item PDC) from natural containers (no container PDC)
 * are counted.</p>
 */
public class NaturalContainerTrackingListener implements Listener {

    private final CollectionLogPlugin plugin;
    private final CollectionMatcher collectionMatcher;
    private final ProgressTrackingManager progressTrackingManager;
    private final NamespacedKey containerKey;
    private final NamespacedKey itemKey;

    public NaturalContainerTrackingListener(CollectionLogPlugin plugin) {
        this.plugin = plugin;
        this.collectionMatcher = plugin.getCollectionMatcher();
        this.progressTrackingManager = plugin.getProgressTrackingManager();
        this.containerKey = new NamespacedKey(plugin, "player_placed_container");
        this.itemKey = new NamespacedKey(plugin, "player_placed_item");
    }

    // ─── Level 1: Container Tracking ─────────────────────────────────────────

    /**
     * Marks block containers placed by players with a permanent PDC tag so they can be
     * distinguished from naturally generated containers.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (!isTrackableContainer(block.getType())) {
            return;
        }
        if (block.getState() instanceof Container container) {
            container.getPersistentDataContainer().set(containerKey, PersistentDataType.BYTE, (byte) 1);
            container.update();
        }
    }

    /**
     * Marks entity containers (e.g. minecart with chest) placed by players with a permanent PDC tag.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof StorageMinecart) {
            entity.getPersistentDataContainer().set(containerKey, PersistentDataType.BYTE, (byte) 1);
        }
    }

    // ─── Level 2 + 3: Item Tagging & Collection Tracking ────────────────────

    /**
     * Handles inventory clicks in trackable containers:
     * <ul>
     *   <li>Tags cursor items placed into natural containers (temporary PDC).</li>
     *   <li>Tracks collection credit when items are taken from natural containers.</li>
     *   <li>Tags items shift-clicked from the player's inventory into a natural container.</li>
     * </ul>
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (collectionMatcher == null || progressTrackingManager == null) {
            return;
        }

        Inventory topInventory = event.getView().getTopInventory();
        if (!isTrackableContainerInventory(topInventory)) {
            return;
        }

        Inventory clickedInventory = event.getClickedInventory();
        if (clickedInventory == null) {
            return;
        }

        InventoryAction action = event.getAction();
        boolean containerIsPlayerPlaced = isPlayerPlacedContainer(topInventory);
        boolean clickedInContainer = clickedInventory.equals(topInventory);

        if (clickedInContainer) {
            handleClickInContainer(player, action, event, containerIsPlayerPlaced);
        } else {
            // Shift-click from player inventory → container: tag the item being sent
            if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY && !containerIsPlayerPlaced) {
                ItemStack item = event.getCurrentItem();
                if (item != null && !item.getType().isAir()) {
                    markItemAsPlayerPlaced(item);
                }
            }
        }
    }

    private void handleClickInContainer(Player player, InventoryAction action,
                                        InventoryClickEvent event, boolean containerIsPlayerPlaced) {
        ItemStack containerItem = event.getCurrentItem();

        // Taking an item out of the container.
        // HOTBAR_SWAP and SWAP_WITH_CURSOR are bidirectional: the container item leaves
        // (tracked here) while another item enters (handled in the sections below).
        boolean isTakeAction = switch (action) {
            case PICKUP_ALL, PICKUP_HALF, PICKUP_ONE, PICKUP_SOME,
                 MOVE_TO_OTHER_INVENTORY,
                 HOTBAR_SWAP, HOTBAR_MOVE_AND_READD,
                 COLLECT_TO_CURSOR,
                 SWAP_WITH_CURSOR -> true;
            default -> false;
        };

        if (isTakeAction && containerItem != null && !containerItem.getType().isAir()) {
            if (!containerIsPlayerPlaced) {
                if (isItemPlayerPlaced(containerItem)) {
                    removePlayerPlacedTag(containerItem);
                } else {
                    trackCollection(player, containerItem);
                    if (containerItem.getType() != Material.ENCHANTED_BOOK) {
                        EnchantmentCollectionTracker.trackEnchantments(
                                plugin,
                                collectionMatcher,
                                progressTrackingManager,
                                player,
                                containerItem,
                                false,
                                "natural container");
                    }
                }
            }
        }

        // Placing the cursor item into the container.
        // SWAP_WITH_CURSOR also appears in isTakeAction above because it simultaneously
        // takes the container item (tracked above) and places the cursor item (tagged here).
        boolean isPlaceAction = switch (action) {
            case PLACE_ALL, PLACE_ONE, PLACE_SOME, SWAP_WITH_CURSOR -> true;
            default -> false;
        };

        if (isPlaceAction && !containerIsPlayerPlaced) {
            ItemStack cursor = event.getCursor();
            if (cursor != null && !cursor.getType().isAir()) {
                markItemAsPlayerPlaced(cursor);
            }
        }

        // For HOTBAR_SWAP the hotbar item goes into the container slot – tag it.
        // (HOTBAR_SWAP is already in isTakeAction to track the container item leaving;
        //  this block handles the other direction: the hotbar item entering the container.)
        if (action == InventoryAction.HOTBAR_SWAP && !containerIsPlayerPlaced) {
            int hotbarSlot = event.getHotbarButton();
            if (hotbarSlot >= 0) {
                ItemStack hotbarItem = event.getView().getBottomInventory().getItem(hotbarSlot);
                if (hotbarItem != null && !hotbarItem.getType().isAir()) {
                    markItemAsPlayerPlaced(hotbarItem);
                }
            }
        }
    }

    /**
     * Tags items dragged into natural containers with a temporary PDC so other players do not
     * receive collection credit when taking them out.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Inventory topInventory = event.getView().getTopInventory();
        if (!isTrackableContainerInventory(topInventory)) {
            return;
        }
        if (isPlayerPlacedContainer(topInventory)) {
            return;
        }

        int topSize = topInventory.getSize();
        boolean dragsIntoContainer = event.getRawSlots().stream().anyMatch(slot -> slot < topSize);
        if (!dragsIntoContainer) {
            return;
        }

        // Tag each item distributed into a container slot
        event.getNewItems().forEach((rawSlot, item) -> {
            if (rawSlot < topSize && item != null && !item.getType().isAir()) {
                markItemAsPlayerPlaced(item);
            }
        });

        // Tag the remaining cursor as well (items not yet placed keep the association)
        ItemStack cursor = event.getCursor();
        if (cursor != null && !cursor.getType().isAir()) {
            markItemAsPlayerPlaced(cursor);
        }
    }

    /**
     * Tags items transferred by hoppers or droppers when either the source or destination is a
     * player-placed container.
     *
     * <p>This prevents two exploitation paths:</p>
     * <ul>
     *   <li>Natural → player-placed: item is tagged because the destination is player-placed.</li>
     *   <li>Player-placed → natural: item is tagged because the source is player-placed, so a
     *       player taking it from the natural container does not receive collection credit.</li>
     * </ul>
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        Inventory source = event.getSource();
        Inventory destination = event.getDestination();
        if (!isTrackableContainerInventory(destination)) {
            return;
        }
        if (isPlayerPlacedContainer(source) || isPlayerPlacedContainer(destination)) {
            markItemAsPlayerPlaced(event.getItem());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || collectionMatcher == null
                || progressTrackingManager == null) {
            return;
        }

        ItemStack item = event.getItem().getItemStack();
        if (!item.getType().name().endsWith(ItemSignatureHelper.ARMOR_TRIM_SMITHING_TEMPLATE_SUFFIX)) {
            return;
        }

        trackCollection(player, item);
    }

    // ─── Helper Methods ───────────────────────────────────────────────────────

    private void trackCollection(Player player, ItemStack item) {
        for (CollectionMatcher.CollectionMatch match : collectionMatcher.findMatches(item)) {
            progressTrackingManager.trackItemCollection(player, match.collection(), match.signature())
                    .exceptionally(throwable -> {
                        plugin.getLogger().severe(
                                "Failed to track natural container item collection: " + throwable.getMessage());
                        return null;
                    });
        }
    }

    private boolean isTrackableContainer(Material material) {
        return switch (material) {
            case CHEST, BARREL, HOPPER, DISPENSER, DROPPER, DECORATED_POT -> true;
            default -> false;
        };
    }

    private boolean isTrackableContainerInventory(Inventory inventory) {
        if (inventory == null) {
            return false;
        }
        return switch (inventory.getType()) {
            case CHEST, BARREL, HOPPER, DISPENSER, DROPPER, DECORATED_POT -> true;
            default -> false;
        };
    }

    private boolean isPlayerPlacedContainer(Inventory inventory) {
        if (inventory == null) {
            return false;
        }
        InventoryHolder holder = inventory.getHolder();

        if (holder instanceof Container container) {
            return container.getPersistentDataContainer().has(containerKey, PersistentDataType.BYTE);
        }

        if (holder instanceof StorageMinecart minecart) {
            return minecart.getPersistentDataContainer().has(containerKey, PersistentDataType.BYTE);
        }

        return false;
    }

    private void markItemAsPlayerPlaced(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
    }

    private void removePlayerPlacedTag(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().remove(itemKey);
        item.setItemMeta(meta);
    }

    private boolean isItemPlayerPlaced(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE);
    }
}
