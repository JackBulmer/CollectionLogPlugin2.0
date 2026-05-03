package collectionlog.gui;

import collectionlog.CollectionLogPlugin;
import collectionlog.managers.InfiniteDyesManager;
import collectionlog.utils.CollectionMatcher;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GUI for the Infinite Dyes perk.
 *
 * <p>Layout (54 slots):
 * <ul>
 *   <li>Slot 13: Input / Output slot (player-interactive)</li>
 *   <li>Slots 19–34: Dye colour buttons (16 colours)</li>
 *   <li>Slot 40: Confirm button (Paper, CMD 24112)</li>
 *   <li>Slot 45: Back button (only when opened from Perks GUI)</li>
 * </ul>
 */
public class InfiniteDyesGUI implements Listener {

    // ── Slots ─────────────────────────────────────────────────────────────────
    private static final int INVENTORY_SIZE  = 54;
    private static final int SLOT_INPUT      = 13;
    private static final int SLOT_DYES_START = 19;
    private static final int SLOT_DYES_END   = 34;
    private static final int SLOT_CONFIRM    = 40;
    private static final int SLOT_BACK       = 45;

    private static final int CONFIRM_CMD = 24112;
    private static final int BACK_CMD    = 24001;

    private static final DyeColor[] COLOR_ORDER = {
            DyeColor.WHITE, DyeColor.ORANGE, DyeColor.MAGENTA, DyeColor.LIGHT_BLUE,
            DyeColor.YELLOW, DyeColor.LIME, DyeColor.PINK, DyeColor.GRAY,
            DyeColor.LIGHT_GRAY, DyeColor.CYAN, DyeColor.PURPLE, DyeColor.BLUE,
            DyeColor.BROWN, DyeColor.GREEN, DyeColor.RED, DyeColor.BLACK
    };

    private static final Material[] DYE_MATERIALS = {
            Material.WHITE_DYE, Material.ORANGE_DYE, Material.MAGENTA_DYE,
            Material.LIGHT_BLUE_DYE, Material.YELLOW_DYE, Material.LIME_DYE,
            Material.PINK_DYE, Material.GRAY_DYE, Material.LIGHT_GRAY_DYE,
            Material.CYAN_DYE, Material.PURPLE_DYE, Material.BLUE_DYE,
            Material.BROWN_DYE, Material.GREEN_DYE, Material.RED_DYE,
            Material.BLACK_DYE
    };

    private final CollectionLogPlugin plugin;
    private final Map<UUID, DyeColor> selectedColors = new HashMap<>();
    private final Map<UUID, Boolean> fromPerksMap = new HashMap<>();

    public InfiniteDyesGUI(CollectionLogPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Public entry point ────────────────────────────────────────────────────

    /**
     * Open the dye GUI for a player.
     *
     * @param player    target player
     * @param fromPerks true if opened from the Perks GUI (shows back button)
     */
    public void open(Player player, boolean fromPerks) {
        UUID id = player.getUniqueId();
        fromPerksMap.put(id, fromPerks);
        selectedColors.putIfAbsent(id, DyeColor.WHITE);
        buildAndOpen(player);
    }

    // ── Build inventory ───────────────────────────────────────────────────────

    private void buildAndOpen(Player player) {
        UUID id = player.getUniqueId();
        DyeColor selected = selectedColors.getOrDefault(id, DyeColor.WHITE);
        boolean fromPerks = fromPerksMap.getOrDefault(id, false);

        Inventory inv = Bukkit.createInventory(new InfiniteDyesHolder(), INVENTORY_SIZE, "");

        // Slot 13 is the input slot – leave it empty (or preserve existing item)
        // (Bukkit initialises all slots to null/air by default)

        // Dye colour buttons (slots 19–34)
        for (int i = 0; i < COLOR_ORDER.length; i++) {
            DyeColor color = COLOR_ORDER[i];
            inv.setItem(SLOT_DYES_START + i, createDyeItem(color, color == selected));
        }

        // Confirm button
        inv.setItem(SLOT_CONFIRM, createConfirmButton(selected, null));

        // Back button
        if (fromPerks) {
            inv.setItem(SLOT_BACK, createBackButton());
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.openInventory(inv);
            }
        });
    }

    // ── Event handling ────────────────────────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof InfiniteDyesHolder)) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int rawSlot = event.getRawSlot();
        int invSize  = event.getInventory().getSize();
        UUID id = player.getUniqueId();

        // ── Clicks in the player's own inventory ──────────────────────────────
        if (rawSlot >= invSize || rawSlot < 0) {
            // Prevent shift-clicking items from player inventory into the GUI
            if (event.getClick().isShiftClick()) {
                event.setCancelled(true);
            }
            return;
        }

        // ── Clicks inside the GUI ─────────────────────────────────────────────

        // Input slot – allow natural Bukkit interaction; update confirm button after
        if (rawSlot == SLOT_INPUT) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Inventory openInv = player.getOpenInventory().getTopInventory();
                if (openInv.getHolder() instanceof InfiniteDyesHolder) {
                    ItemStack inputItem = openInv.getItem(SLOT_INPUT);
                    DyeColor sel = selectedColors.getOrDefault(id, DyeColor.WHITE);
                    openInv.setItem(SLOT_CONFIRM, createConfirmButton(sel, inputItem));
                }
            }, 1L);
            return; // do NOT cancel – let Bukkit handle the item interaction
        }

        event.setCancelled(true);

        // Dye colour selection (slots 19–34)
        if (rawSlot >= SLOT_DYES_START && rawSlot <= SLOT_DYES_END) {
            int idx = rawSlot - SLOT_DYES_START;
            if (idx < COLOR_ORDER.length) {
                DyeColor chosen = COLOR_ORDER[idx];
                selectedColors.put(id, chosen);

                // Refresh dye buttons and confirm in the open inventory
                Inventory openInv = player.getOpenInventory().getTopInventory();
                for (int i = 0; i < COLOR_ORDER.length; i++) {
                    openInv.setItem(SLOT_DYES_START + i,
                            createDyeItem(COLOR_ORDER[i], COLOR_ORDER[i] == chosen));
                }
                ItemStack inputItem = openInv.getItem(SLOT_INPUT);
                openInv.setItem(SLOT_CONFIRM, createConfirmButton(chosen, inputItem));
            }
            return;
        }

        // Confirm button
        if (rawSlot == SLOT_CONFIRM) {
            handleConfirm(player, event.getInventory());
            return;
        }

        // Back button
        if (rawSlot == SLOT_BACK && fromPerksMap.getOrDefault(id, false)) {
            // Return any item in the input slot before closing
            returnInputItem(player, event.getInventory());
            fromPerksMap.remove(id);
            selectedColors.remove(id);
            player.closeInventory();
            plugin.getPerksGUI().openMainPage(player);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof InfiniteDyesHolder)) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        UUID id = player.getUniqueId();
        returnInputItem(player, event.getInventory());
        fromPerksMap.remove(id);
        selectedColors.remove(id);
    }

    // ── Confirm logic ─────────────────────────────────────────────────────────

    private void handleConfirm(Player player, Inventory inv) {
        UUID id = player.getUniqueId();
        DyeColor selected = selectedColors.getOrDefault(id, DyeColor.WHITE);
        InfiniteDyesManager dyesManager = plugin.getInfiniteDyesManager();
        ItemStack inputItem = inv.getItem(SLOT_INPUT);

        // No item in input slot
        if (inputItem == null || inputItem.getType() == Material.AIR) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return;
        }

        // Item is not dyeable
        if (!dyesManager.isDyeable(inputItem)) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return;
        }

        // Banner with patterns
        if (dyesManager.hasBannerPatterns(inputItem)) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return;
        }

        // Dye the item
        ItemStack dyed = dyesManager.dyeItem(inputItem, selected);
        if (dyed == null) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return;
        }

        // Replace input slot with dyed item
        inv.setItem(SLOT_INPUT, dyed);

        // Track collection progress if enabled
        if (plugin.getConfig().getBoolean("track_dye_collection_progress", false)) {
            trackDyeProgress(player, dyed);
        }

        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);

        // Update confirm button to reflect new state
        inv.setItem(SLOT_CONFIRM, createConfirmButton(selected, dyed));
    }

    private void trackDyeProgress(Player player, ItemStack dyed) {
        if (plugin.getProgressTrackingManager() == null) return;
        if (plugin.getCollectionMatcher() == null) return;

        var matches = plugin.getCollectionMatcher().findMatches(dyed);
        if (matches == null || matches.isEmpty()) return;

        for (var match : matches) {
            plugin.getProgressTrackingManager().trackItemCollection(
                    player, match.collection(), match.signature());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void returnInputItem(Player player, Inventory inv) {
        ItemStack inputItem = inv.getItem(SLOT_INPUT);
        if (inputItem != null && inputItem.getType() != Material.AIR) {
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(inputItem.clone());
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
            inv.setItem(SLOT_INPUT, null);
        }
    }

    // ── Item builders ─────────────────────────────────────────────────────────

    private ItemStack createDyeItem(DyeColor color, boolean isSelected) {
        int colorIndex = indexOfColor(color);
        Material mat = colorIndex >= 0 ? DYE_MATERIALS[colorIndex] : Material.WHITE_DYE;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(colorChatCode(color) + formatColorName(color));
        meta.setLore(List.of("§7Click to select this colour."));

        if (isSelected) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createConfirmButton(DyeColor selected, ItemStack inputItem) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setCustomModelData(CONFIRM_CMD);
        String colorCode = colorChatCode(selected);
        meta.setDisplayName(colorCode + "Confirm: " + formatColorName(selected));

        List<String> lore = new ArrayList<>();
        lore.add(colorCode + formatColorName(selected));

        if (inputItem == null || inputItem.getType() == Material.AIR) {
            lore.add("§cNo Items Entered");
        } else {
            InfiniteDyesManager dyesManager = plugin.getInfiniteDyesManager();
            if (!dyesManager.isDyeable(inputItem)) {
                lore.add("§cUnsupported Item");
            } else if (dyesManager.hasBannerPatterns(inputItem)) {
                lore.add("§cBanners with patterns cannot be dyed");
            } else {
                lore.add("§7Click to dye " + inputItem.getAmount() + "x "
                        + formatMaterialName(inputItem.getType()));
            }
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(BACK_CMD);
            meta.setDisplayName("§c◀ Back to Perks");
            item.setItemMeta(meta);
        }
        return item;
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private int indexOfColor(DyeColor color) {
        for (int i = 0; i < COLOR_ORDER.length; i++) {
            if (COLOR_ORDER[i] == color) return i;
        }
        return -1;
    }

    private String formatColorName(DyeColor color) {
        String raw = color.name().replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        for (String word : raw.split(" ")) {
            if (!word.isEmpty()) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) sb.append(word.substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }

    private String formatMaterialName(Material material) {
        String raw = material.name().replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        for (String word : raw.split(" ")) {
            if (!word.isEmpty()) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) sb.append(word.substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }

    private String colorChatCode(DyeColor color) {
        // Note: ChatColor has limited options – PINK and MAGENTA share §d (light purple)
        // since Minecraft's chat colour palette does not have a distinct pink code.
        return switch (color) {
            case WHITE      -> "§f";
            case ORANGE     -> "§6";
            case MAGENTA    -> "§d";
            case LIGHT_BLUE -> "§b";
            case YELLOW     -> "§e";
            case LIME       -> "§a";
            case PINK       -> "§d"; // no distinct pink in legacy chat colours
            case GRAY       -> "§8";
            case LIGHT_GRAY -> "§7";
            case CYAN       -> "§3";
            case PURPLE     -> "§5";
            case BLUE       -> "§9";
            case BROWN      -> "§6";
            case GREEN      -> "§2";
            case RED        -> "§c";
            case BLACK      -> "§0";
        };
    }

    // ── Inner types ───────────────────────────────────────────────────────────

    private static final class InfiniteDyesHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
