package collectionlog.listeners;

import collectionlog.CollectionLogPlugin;
import collectionlog.perks.PerkManager;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listener for the Openable Shulkers perk.
 * Allows players to open shulker boxes directly from their hand without placing them.
 */
public class OpenableShulkersListener implements Listener {

    private static final String PERK_ID = "openable_shulkers";
    private static final String NESTED_SHULKER_MESSAGE = "§cYou cannot put a shulker box inside another shulker box!";

    private final CollectionLogPlugin plugin;
    private final PerkManager perkManager;

    // Track which players have open shulker inventories and which hand slot they came from
    private final Map<UUID, ShulkerContext> openShulkers = new ConcurrentHashMap<>();

    public OpenableShulkersListener(CollectionLogPlugin plugin) {
        this.plugin = plugin;
        this.perkManager = plugin.getPerkManager();
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();

        // Only process right-clicks
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        // Ensure this only runs once from the main hand
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item == null || item.getType().isAir() || !isShulkerBox(item.getType())) {
            return;
        }

        // Check if player has perk unlocked
        UUID playerId = player.getUniqueId();
        if (!perkManager.hasUnlocked(playerId, PERK_ID)) {
            return;
        }

        // If player is sneaking, allow normal placement
        if (player.isSneaking()) {
            return;
        }

        // Cancel the event to prevent placement
        event.setCancelled(true);

        // Open the shulker box GUI
        openShulkerBox(player, item);
    }

    /**
     * Opens a shulker box inventory for the player.
     * Reads the contents from the ItemStack's NBT data and creates a GUI.
     */
    private void openShulkerBox(Player player, ItemStack shulkerItem) {
        // Get the shulker box's display name (or default to material name)
        String title = shulkerItem.hasItemMeta() && shulkerItem.getItemMeta().hasDisplayName()
                ? shulkerItem.getItemMeta().getDisplayName()
                : getShulkerBoxName(shulkerItem.getType());

        // Create a shulker-box inventory for vanilla-consistent behavior
        Inventory inventory = plugin.getServer().createInventory(null, InventoryType.SHULKER_BOX, title);

        // Load contents from ItemStack NBT if present
        if (shulkerItem.hasItemMeta() && shulkerItem.getItemMeta() instanceof BlockStateMeta blockStateMeta) {
            if (blockStateMeta.getBlockState() instanceof ShulkerBox shulkerBox) {
                // Copy contents from the shulker box state to the inventory
                ItemStack[] contents = shulkerBox.getInventory().getContents();
                inventory.setContents(contents);
            }
        }

        // Track that this player has an open shulker (so we can save changes on close)
        ShulkerContext previous = openShulkers.put(player.getUniqueId(), new ShulkerContext(shulkerItem, inventory));
        // If another shulker was already tracked for this player (e.g. rapid opens),
        // save it before replacing so its contents are not lost.
        if (previous != null) {
            saveShulkerContents(player, previous.shulkerItem, previous.inventory);
        }

        // Open the inventory for the player
        player.openInventory(inventory);
    }

    /**
     * When the player closes the shulker inventory, save the contents back to the ItemStack.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        UUID playerId = player.getUniqueId();
        ShulkerContext context = openShulkers.remove(playerId);

        if (context == null) {
            return; // Player wasn't viewing a shulker from this listener
        }

        // Save the inventory contents back to the ItemStack
        saveShulkerContents(player, context.shulkerItem, context.inventory);
    }

    /**
     * Prevent players from placing shulker boxes inside opened shulker inventories.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ShulkerContext context = openShulkers.get(player.getUniqueId());
        if (context == null) {
            return;
        }

        Inventory topInventory = event.getView().getTopInventory();
        if (!context.inventory.equals(topInventory)) {
            return;
        }

        Inventory clickedInventory = event.getClickedInventory();

        if (clickedInventory != null && clickedInventory.equals(topInventory)) {
            if (isShulkerItem(event.getCursor())) {
                event.setCancelled(true);
                player.sendMessage(NESTED_SHULKER_MESSAGE);
                return;
            }

            if (event.getClick() == ClickType.SWAP_OFFHAND && isShulkerItem(player.getInventory().getItemInOffHand())) {
                event.setCancelled(true);
                player.sendMessage(NESTED_SHULKER_MESSAGE);
                return;
            }

            if (event.getClick().isKeyboardClick()) {
                int hotbarButton = event.getHotbarButton();
                if (hotbarButton >= 0 && isShulkerItem(player.getInventory().getItem(hotbarButton))) {
                    event.setCancelled(true);
                    player.sendMessage(NESTED_SHULKER_MESSAGE);
                    return;
                }
            }
        }

        if (event.isShiftClick()
                && clickedInventory != null
                && clickedInventory.equals(event.getView().getBottomInventory())
                && isShulkerItem(event.getCurrentItem())) {
            event.setCancelled(true);
            player.sendMessage(NESTED_SHULKER_MESSAGE);
        }
    }

    /**
     * Prevent dragging shulker boxes into opened shulker inventories.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ShulkerContext context = openShulkers.get(player.getUniqueId());
        if (context == null || !context.inventory.equals(event.getView().getTopInventory())) {
            return;
        }

        if (!isShulkerItem(event.getOldCursor())) {
            return;
        }

        int topInventorySize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topInventorySize) {
                event.setCancelled(true);
                player.sendMessage(NESTED_SHULKER_MESSAGE);
                return;
            }
        }
    }

    /**
     * Saves the inventory contents back to the shulker box ItemStack's NBT data.
     */
    private void saveShulkerContents(Player player, ItemStack shulkerItem, Inventory inventory) {
        // Get or create BlockStateMeta
        BlockStateMeta meta = (BlockStateMeta) (shulkerItem.hasItemMeta()
                ? shulkerItem.getItemMeta()
                : plugin.getServer().getItemFactory().getItemMeta(shulkerItem.getType()));

        if (meta == null) {
            return;
        }

        // Get the ShulkerBox block state
        ShulkerBox shulkerBox = (ShulkerBox) meta.getBlockState();

        // Update the shulker box contents
        ItemStack[] contents = inventory.getContents();
        shulkerBox.getInventory().setContents(contents);

        // Update the block state in meta
        meta.setBlockState(shulkerBox);

        // Apply meta back to the item
        shulkerItem.setItemMeta(meta);

        // Update the item in the player's main hand unconditionally – we know the
        // shulker was opened from the main hand, so this is always the right slot.
        player.getInventory().setItemInMainHand(shulkerItem);
    }

    /**
     * Check if a material is a shulker box (any color).
     */
    private boolean isShulkerBox(Material material) {
        return switch (material) {
            case SHULKER_BOX,
                 WHITE_SHULKER_BOX,
                 ORANGE_SHULKER_BOX,
                 MAGENTA_SHULKER_BOX,
                 LIGHT_BLUE_SHULKER_BOX,
                 YELLOW_SHULKER_BOX,
                 LIME_SHULKER_BOX,
                 PINK_SHULKER_BOX,
                 GRAY_SHULKER_BOX,
                 LIGHT_GRAY_SHULKER_BOX,
                 CYAN_SHULKER_BOX,
                 PURPLE_SHULKER_BOX,
                 BLUE_SHULKER_BOX,
                 BROWN_SHULKER_BOX,
                 GREEN_SHULKER_BOX,
                 RED_SHULKER_BOX,
                 BLACK_SHULKER_BOX -> true;
            default -> false;
        };
    }

    /**
     * Check if an ItemStack is a shulker box.
     */
    private boolean isShulkerItem(ItemStack itemStack) {
        return itemStack != null && !itemStack.getType().isAir() && isShulkerBox(itemStack.getType());
    }

    /**
     * Get the display name for a shulker box type.
     */
    private String getShulkerBoxName(Material material) {
        return switch (material) {
            case SHULKER_BOX -> "Shulker Box";
            case WHITE_SHULKER_BOX -> "White Shulker Box";
            case ORANGE_SHULKER_BOX -> "Orange Shulker Box";
            case MAGENTA_SHULKER_BOX -> "Magenta Shulker Box";
            case LIGHT_BLUE_SHULKER_BOX -> "Light Blue Shulker Box";
            case YELLOW_SHULKER_BOX -> "Yellow Shulker Box";
            case LIME_SHULKER_BOX -> "Lime Shulker Box";
            case PINK_SHULKER_BOX -> "Pink Shulker Box";
            case GRAY_SHULKER_BOX -> "Gray Shulker Box";
            case LIGHT_GRAY_SHULKER_BOX -> "Light Gray Shulker Box";
            case CYAN_SHULKER_BOX -> "Cyan Shulker Box";
            case PURPLE_SHULKER_BOX -> "Purple Shulker Box";
            case BLUE_SHULKER_BOX -> "Blue Shulker Box";
            case BROWN_SHULKER_BOX -> "Brown Shulker Box";
            case GREEN_SHULKER_BOX -> "Green Shulker Box";
            case RED_SHULKER_BOX -> "Red Shulker Box";
            case BLACK_SHULKER_BOX -> "Black Shulker Box";
            default -> "Shulker Box";
        };
    }

    /**
     * Context for tracking open shulker inventories.
     */
    private static class ShulkerContext {
        final ItemStack shulkerItem;
        final Inventory inventory;

        ShulkerContext(ItemStack shulkerItem, Inventory inventory) {
            this.shulkerItem = shulkerItem;
            this.inventory = inventory;
        }
    }
}
