package collectionlog.gui;

import collectionlog.CollectionLogPlugin;
import collectionlog.perks.Perk;
import collectionlog.perks.PerkManager;
import collectionlog.perks.PerkRegistry;
import collectionlog.tags.Tag;
import collectionlog.tags.TagManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * GUI for viewing and toggling player perks.
 *
 * <p>The main page shows all non-potion perks plus navigation buttons.
 * The potion-effects sub-page shows potion-effect perks.
 * Both pages include a shortcut to the Tags GUI.
 */
public class PerksGUI implements Listener {

    // ── Layout constants ──────────────────────────────────────────────────────

    private static final int INVENTORY_SIZE = 54;
    /** Number of perk slots per page (rows 0–4). */
    private static final int PAGE_SLOTS = 45;

    // Bottom row (row 5)
    private static final int SLOT_PREV        = 45;
    private static final int SLOT_TAGS        = 47;
    private static final int SLOT_POTION_PAGE = 48;
    private static final int SLOT_CLOSE       = 49;
    private static final int SLOT_FILTER      = 50;
    private static final int SLOT_NEXT        = 53;

    private static final int BACK_ICON_CUSTOM_MODEL_DATA   = 24001;
    private static final int FILTER_ICON_CUSTOM_MODEL_DATA = 100001;

    private static final String TITLE_MAIN   = ChatColor.DARK_PURPLE + "✦ Perks";
    private static final String TITLE_POTION = ChatColor.DARK_AQUA   + "✦ Potion Effects";

    // ── State ─────────────────────────────────────────────────────────────────

    private final CollectionLogPlugin plugin;
    private final PerkManager perkManager;
    private final TagManager tagManager;
    private final InventoryHolder holder = new PerksGuiHolder();
    private final Map<UUID, PerksGUIState> states = new HashMap<>();

    public PerksGUI(CollectionLogPlugin plugin, PerkManager perkManager, TagManager tagManager) {
        this.plugin = plugin;
        this.perkManager = perkManager;
        this.tagManager = tagManager;
    }

    // ── Public entry points ───────────────────────────────────────────────────

    /** Open the main perks page for the given player. */
    public void openMainPage(Player player) {
        openMainPage(player, 0, false);
    }

    /** Open the potion effects sub-page for the given player. */
    public void openPotionPage(Player player) {
        openPotionPage(player, 0, false);
    }

    // ── Internal open methods ─────────────────────────────────────────────────

    private void openMainPage(Player player, int page, boolean showUnlockedOnly) {
        UUID id = player.getUniqueId();
        List<Perk> perks = PerkRegistry.getNonPotionPerks();
        buildAndOpen(player, perks, page, showUnlockedOnly, false, TITLE_MAIN);
        states.put(id, new PerksGUIState(page, showUnlockedOnly, false));
    }

    private void openPotionPage(Player player, int page, boolean showUnlockedOnly) {
        UUID id = player.getUniqueId();
        List<Perk> perks = PerkRegistry.getPotionEffectPerks();
        buildAndOpen(player, perks, page, showUnlockedOnly, true, TITLE_POTION);
        states.put(id, new PerksGUIState(page, showUnlockedOnly, true));
    }

    private void buildAndOpen(Player player, List<Perk> allPerks, int page,
                              boolean showUnlockedOnly, boolean isPotionPage, String title) {
        UUID playerId = player.getUniqueId();

        List<Perk> perksToShow = showUnlockedOnly
                ? allPerks.stream().filter(p -> perkManager.hasUnlocked(playerId, p.getId())).toList()
                : allPerks;

        int totalPages = Math.max(1, (int) Math.ceil((double) perksToShow.size() / PAGE_SLOTS));
        int clampedPage = Math.max(0, Math.min(page, totalPages - 1));

        int start = clampedPage * PAGE_SLOTS;
        int end = Math.min(start + PAGE_SLOTS, perksToShow.size());

        Inventory inv = Bukkit.createInventory(holder, INVENTORY_SIZE, title);

        // Perk items
        for (int i = start; i < end; i++) {
            Perk perk = perksToShow.get(i);
            inv.setItem(i - start, createPerkItem(perk, playerId));
        }

        // Navigation – previous
        if (clampedPage > 0) {
            inv.setItem(SLOT_PREV, createButton(Material.ARROW, "§6◀ Previous Page", null));
        }

        // Navigation – next
        if (clampedPage < totalPages - 1) {
            inv.setItem(SLOT_NEXT, createButton(Material.ARROW, "§6Next Page ▶", null));
        }

        // Tags shortcut
        inv.setItem(SLOT_TAGS, createButton(Material.NAME_TAG, "§eOpen Tags", List.of("§7Click to manage your tags.")));

        // Potion / back button
        if (isPotionPage) {
            inv.setItem(SLOT_POTION_PAGE, createBackButton("§c◀ Back to Perks"));
        } else {
            inv.setItem(SLOT_POTION_PAGE, createButton(Material.POTION,
                    "§bPotion Effects",
                    List.of("§7Click to view potion-effect perks.")));
        }

        // Close button
        inv.setItem(SLOT_CLOSE, createCloseButton());

        // Filter toggle
        inv.setItem(SLOT_FILTER, createFilterButton(showUnlockedOnly));

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.openInventory(inv);
            }
        });
    }

    // ── Event handling ────────────────────────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof PerksGuiHolder)) {
            return;
        }
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        UUID playerId = player.getUniqueId();
        PerksGUIState state = states.get(playerId);
        if (state == null) {
            return;
        }

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= INVENTORY_SIZE) {
            return;
        }

        boolean isPotionPage = state.potionPage();
        int page = state.page();
        boolean showUnlockedOnly = state.showUnlockedOnly();

        if (slot == SLOT_PREV) {
            if (isPotionPage) {
                openPotionPage(player, page - 1, showUnlockedOnly);
            } else {
                openMainPage(player, page - 1, showUnlockedOnly);
            }
            return;
        }

        if (slot == SLOT_NEXT) {
            if (isPotionPage) {
                openPotionPage(player, page + 1, showUnlockedOnly);
            } else {
                openMainPage(player, page + 1, showUnlockedOnly);
            }
            return;
        }

        if (slot == SLOT_TAGS) {
            plugin.getTagsGUI().openTagsGUIFromPerks(player);
            return;
        }

        if (slot == SLOT_POTION_PAGE) {
            if (isPotionPage) {
                openMainPage(player, 0, showUnlockedOnly);
            } else {
                openPotionPage(player, 0, showUnlockedOnly);
            }
            return;
        }

        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }

        if (slot == SLOT_FILTER) {
            if (isPotionPage) {
                openPotionPage(player, 0, !showUnlockedOnly);
            } else {
                openMainPage(player, 0, !showUnlockedOnly);
            }
            return;
        }

        // Perk slot clicked
        if (slot < PAGE_SLOTS) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) {
                return;
            }
            handlePerkClick(player, clicked, isPotionPage, page, showUnlockedOnly);
        }
    }

    private void handlePerkClick(Player player, ItemStack clicked, boolean isPotionPage,
                                 int page, boolean showUnlockedOnly) {
        UUID playerId = player.getUniqueId();
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) {
            return;
        }

        List<String> lore = meta.getLore();
        if (lore == null) {
            return;
        }

        // Identify perk from lore ID line (hidden in lore)
        String perkId = extractPerkId(lore);
        if (perkId == null) {
            return;
        }

        Perk perk = PerkRegistry.getPerk(perkId);
        if (perk == null) {
            return;
        }

        if (!perkManager.hasUnlocked(playerId, perkId)) {
            player.sendMessage("§cThis perk is locked. Complete the required collection to unlock it.");
            return;
        }

        if (!perk.isToggleable()) {
            player.sendMessage("§cThis perk cannot be toggled.");
            return;
        }

        boolean newState = perkManager.togglePerk(playerId, perkId);
        player.sendMessage(newState
                ? "§a✦ " + perk.getName() + " §aenabled."
                : "§7✦ " + perk.getName() + " §7disabled.");

        // Apply or remove the effect immediately for potion-effect perks
        if (perk.isPotionEffect()) {
            plugin.getPotionEffectManager().onPerkToggled(player, perkId, newState);
        }

        // Refresh the current page
        if (isPotionPage) {
            openPotionPage(player, page, showUnlockedOnly);
        } else {
            openMainPage(player, page, showUnlockedOnly);
        }
    }

    // ── Item builders ─────────────────────────────────────────────────────────

    private ItemStack createPerkItem(Perk perk, UUID playerId) {
        boolean unlocked = perkManager.hasUnlocked(playerId, perk.getId());
        boolean enabled  = unlocked && perkManager.isEnabled(playerId, perk.getId());

        // Use potion item with appropriate type for potion-effect perks
        ItemStack item;
        PotionType potionType = getPotionTypeForPerk(perk.getId());
        if (potionType != null) {
            item = new ItemStack(Material.POTION);
            PotionMeta pm = (PotionMeta) item.getItemMeta();
            if (pm != null) {
                pm.setBasePotionType(potionType);
                item.setItemMeta(pm);
            }
        } else {
            item = new ItemStack(perk.getIcon());
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        // Name colour: gold = unlocked & enabled, grey = unlocked & disabled, red = locked
        String nameColour = unlocked ? (enabled ? "§6" : "§7") : "§c";
        meta.setDisplayName(nameColour + perk.getName());

        List<String> lore = new ArrayList<>(perk.getDescription());
        lore.add("");

        if (unlocked) {
            if (perk.isToggleable()) {
                lore.add(enabled ? "§aEnabled §7– click to disable" : "§cDisabled §7– click to enable");
            } else {
                lore.add("§aUnlocked");
            }
        } else {
            lore.add("§c✗ Locked");
            if (perk.getRequiredCollection() != null) {
                lore.add("§7Requires: §e" + formatCollectionName(perk.getRequiredCollection()));
            }
        }

        // Hidden ID line so click handler can find the perk
        lore.add("§0perk:" + perk.getId());

        meta.setLore(lore);

        if (enabled) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createButton(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) {
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createCloseButton() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(BACK_ICON_CUSTOM_MODEL_DATA);
            meta.setDisplayName("§cClose");
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createBackButton(String name) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(BACK_ICON_CUSTOM_MODEL_DATA);
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createFilterButton(boolean showUnlockedOnly) {
        ItemStack item = new ItemStack(showUnlockedOnly ? Material.PAPER : Material.HOPPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (showUnlockedOnly) {
                meta.setCustomModelData(FILTER_ICON_CUSTOM_MODEL_DATA);
            }
            meta.setDisplayName(showUnlockedOnly ? "§eShowing: §aUnlocked Only" : "§eShowing: §fAll Perks");
            meta.setLore(List.of("§7Click to toggle perk filter"));
            item.setItemMeta(meta);
        }
        return item;
    }

    private PotionType getPotionTypeForPerk(String perkId) {
        // permanent_dolphins_grace uses HEART_OF_THE_SEA because no vanilla Dolphin's Grace potion exists
        return switch (perkId) {
            case "permanent_fire_resistance" -> PotionType.FIRE_RESISTANCE;
            case "permanent_water_breathing" -> PotionType.WATER_BREATHING;
            case "permanent_night_vision"    -> PotionType.NIGHT_VISION;
            case "permanent_slow_falling"    -> PotionType.SLOW_FALLING;
            default -> null;
        };
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Extracts the hidden perk ID from a lore line formatted as §0perk:<id>. */
    private String extractPerkId(List<String> lore) {
        for (String line : lore) {
            if (line.startsWith("§0perk:")) {
                return line.substring("§0perk:".length());
            }
        }
        return null;
    }

    private String formatCollectionName(String collectionId) {
        if (collectionId == null || collectionId.isEmpty()) {
            return "None";
        }
        String[] parts = collectionId.split(":");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) sb.append(" › ");
            String[] words = part.split("[ _]");
            for (String word : words) {
                if (word.isEmpty()) continue;
                if (sb.length() > 0 && !sb.toString().endsWith("› ")) sb.append(' ');
                sb.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) sb.append(word.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return sb.toString();
    }

    /** Remove cached state when the player closes the inventory. */
    public void cleanup(UUID playerId) {
        states.remove(playerId);
    }

    // ── Inner types ───────────────────────────────────────────────────────────

    private record PerksGUIState(int page, boolean showUnlockedOnly, boolean potionPage) {
    }

    private static final class PerksGuiHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
