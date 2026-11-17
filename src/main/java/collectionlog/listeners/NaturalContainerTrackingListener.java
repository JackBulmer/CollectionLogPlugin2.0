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
import org.bukkit.block.DoubleChest;
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
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
 *
 * <p>Item tags exist only so items can be recognised while they sit in a natural container
 * (or on the ground after the container is broken). A tagged item must never stay in a
 * player's inventory, because the extra data stops it stacking with identical untagged
 * items. Besides removing the tag when an item is taken out, this listener therefore sweeps
 * stray tags from player inventories (on join, after a container closes and after a tagged
 * item is picked up) and from player-placed containers (when they are opened).</p>
 */
public class NaturalContainerTrackingListener implements Listener {

    private final CollectionLogPlugin plugin;
    private final CollectionMatcher collectionMatcher;
    private final ProgressTrackingManager progressTrackingManager;
    private final NamespacedKey containerKey;
    private final NamespacedKey itemKey;

    /** Players that already have a tag sweep queued for the next tick. */
    private final Set<UUID> pendingSweeps = ConcurrentHashMap.newKeySet();

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

        // The leftover cursor stack is deliberately NOT tagged. It is still the player's own
        // item, and tagging it would leave a tagged stack in their inventory. If they place it
        // into a natural container later, that click tags it then.
    }

    /**
     * Tags items that hoppers or droppers move from a player-placed container into a natural
     * container, so a player taking them out of the natural container does not receive
     * collection credit.
     *
     * <p>Items moved into a player-placed container are never tagged. Collection credit is never
     * awarded from player-placed containers, so a tag there has no purpose and would only stop
     * the item stacking inside player storage and sorting systems.</p>
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        Inventory source = event.getSource();
        Inventory destination = event.getDestination();
        if (!isTrackableContainerInventory(destination)) {
            return;
        }
        if (isPlayerPlacedContainer(source) && !isPlayerPlacedContainer(destination)) {
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

    // ─── Tag cleanup (keeps tagged items out of player inventories) ──────────

    /**
     * Cleans up tags left behind by earlier versions or interrupted interactions.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        sweepPlayer(event.getPlayer());
    }

    /**
     * Placing part of a stack, shift-clicking into a full container and similar actions can
     * leave a tagged remainder in the player's inventory, so sweep once the container closes.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)
                || !isTrackableContainerInventory(event.getInventory())) {
            return;
        }
        scheduleSweep(player);
    }

    /**
     * Tagged items can also reach a player from the ground, e.g. when a natural container
     * holding player-placed items is broken. The pickup handlers above still see the tag, so
     * the sweep is deferred to the next tick, after they have run.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTaggedItemPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player
                && isItemPlayerPlaced(event.getItem().getItemStack())) {
            scheduleSweep(player);
        }
    }

    /**
     * Items in a player-placed container are never tagged by this listener, and no collection
     * credit is ever awarded from them, so any tag found there is a leftover (for example from
     * a double chest that used to be mistaken for a natural container). Remove it.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        Inventory inventory = event.getInventory();
        if (isTrackableContainerInventory(inventory) && isPlayerPlacedContainer(inventory)) {
            stripTags(inventory);
        }
    }

    private void scheduleSweep(Player player) {
        UUID playerId = player.getUniqueId();
        if (!pendingSweeps.add(playerId)) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            pendingSweeps.remove(playerId);
            if (player.isOnline()) {
                sweepPlayer(player);
            }
        });
    }

    private void sweepPlayer(Player player) {
        stripTags(player.getInventory());
        stripTags(player.getEnderChest());
    }

    private void stripTags(Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (isItemPlayerPlaced(stack)) {
                ItemStack cleaned = stack.clone();
                removePlayerPlacedTag(cleaned);
                inventory.setItem(slot, cleaned);
            }
        }
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

        // A double chest's holder is a DoubleChest wrapping two Chest halves; it is not itself
        // a Container, so each half has to be checked. The whole chest counts as player-placed
        // if either half is, so attaching a chest to a natural one cannot be used to bypass it.
        if (holder instanceof DoubleChest doubleChest) {
            return hasContainerMark(doubleChest.getLeftSide())
                    || hasContainerMark(doubleChest.getRightSide());
        }

        return hasContainerMark(holder);
    }

    private boolean hasContainerMark(InventoryHolder holder) {
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
