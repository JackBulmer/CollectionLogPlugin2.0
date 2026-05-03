package collectionlog.gui;

import collectionlog.CollectionLogPlugin;
import collectionlog.managers.GlowingManager;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.DyeColor;
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
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GUI for choosing a glow colour (Glowing perk).
 *
 * <p>Layout (54 slots):
 * <ul>
 *   <li>Slots 1–16 (0-indexed): Coloured leather tunics representing each dye colour</li>
 *   <li>Slot 49: Barrier to turn glowing off</li>
 *   <li>Slot 45: Back button (only shown when opened from the Perks GUI)</li>
 * </ul>
 */
public class GlowingGUI implements Listener {

    // ── Slots ─────────────────────────────────────────────────────────────────
    private static final int INVENTORY_SIZE = 54;
    private static final int SLOT_COLORS_START = 1;
    private static final int SLOT_COLORS_END   = 16;
    private static final int SLOT_BARRIER      = 49;
    private static final int SLOT_BACK         = 45;

    private static final int BACK_ITEM_CMD     = 24001;

    // ── Preset leather tunic colours (match InfiniteDyes presets) ────────────
    private static final Map<DyeColor, Integer> LEATHER_COLORS = new HashMap<>();
    static {
        LEATHER_COLORS.put(DyeColor.WHITE,      16383998);
        LEATHER_COLORS.put(DyeColor.ORANGE,     16351261);
        LEATHER_COLORS.put(DyeColor.MAGENTA,    13061821);
        LEATHER_COLORS.put(DyeColor.LIGHT_BLUE,  3847130);
        LEATHER_COLORS.put(DyeColor.YELLOW,     16701501);
        LEATHER_COLORS.put(DyeColor.LIME,        8439583);
        LEATHER_COLORS.put(DyeColor.PINK,       15961002);
        LEATHER_COLORS.put(DyeColor.GRAY,        4673362);
        LEATHER_COLORS.put(DyeColor.LIGHT_GRAY, 10329495);
        LEATHER_COLORS.put(DyeColor.CYAN,        1481884);
        LEATHER_COLORS.put(DyeColor.PURPLE,      8991416);
        LEATHER_COLORS.put(DyeColor.BLUE,        3949738);
        LEATHER_COLORS.put(DyeColor.BROWN,       8606770);
        LEATHER_COLORS.put(DyeColor.GREEN,       6192150);
        LEATHER_COLORS.put(DyeColor.RED,        11546150);
        LEATHER_COLORS.put(DyeColor.BLACK,       1908001);
    }

    private final CollectionLogPlugin plugin;
    private final Map<UUID, Boolean> fromPerksMap = new HashMap<>();

    public GlowingGUI(CollectionLogPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Public entry points ───────────────────────────────────────────────────

    /**
     * Open the glowing colour selection GUI for a player.
     *
     * @param player    the player to open it for
     * @param fromPerks whether this was opened from the Perks GUI (shows back button)
     */
    public void open(Player player, boolean fromPerks) {
        UUID playerId = player.getUniqueId();
        fromPerksMap.put(playerId, fromPerks);
        buildAndOpen(player);
    }

    // ── Build inventory ───────────────────────────────────────────────────────

    private void buildAndOpen(Player player) {
        UUID playerId = player.getUniqueId();
        GlowingManager glowingManager = plugin.getGlowingManager();
        String currentColor = glowingManager.getGlowColor(playerId);
        boolean glowEnabled = glowingManager.isGlowEnabled(playerId);
        boolean fromPerks = fromPerksMap.getOrDefault(playerId, false);

        Inventory inv = Bukkit.createInventory(new GlowingGuiHolder(), INVENTORY_SIZE, "");

        // Color tunic slots (1–16)
        DyeColor[] colors = GlowingManager.getColorOrder();
        for (int i = 0; i < colors.length; i++) {
            DyeColor color = colors[i];
            boolean isActive = glowEnabled && color.name().equalsIgnoreCase(currentColor);
            inv.setItem(SLOT_COLORS_START + i, createTunicItem(color, isActive));
        }

        // Barrier – off button
        inv.setItem(SLOT_BARRIER, createBarrierItem(!glowEnabled));

        // Back button (only when opened from perks GUI)
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
        if (!(event.getInventory().getHolder() instanceof GlowingGuiHolder)) {
            return;
        }
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= INVENTORY_SIZE) {
            return;
        }

        UUID playerId = player.getUniqueId();
        GlowingManager glowingManager = plugin.getGlowingManager();

        // Colour tunic clicked
        if (slot >= SLOT_COLORS_START && slot <= SLOT_COLORS_END) {
            int colorIndex = slot - SLOT_COLORS_START;
            DyeColor[] colors = GlowingManager.getColorOrder();
            if (colorIndex < colors.length) {
                DyeColor chosen = colors[colorIndex];
                glowingManager.setGlowColor(playerId, chosen.name());
                buildAndOpen(player);
            }
            return;
        }

        // Barrier – turn off glowing
        if (slot == SLOT_BARRIER) {
            glowingManager.toggleGlow(playerId, false);
            buildAndOpen(player);
            return;
        }

        // Back button
        if (slot == SLOT_BACK && fromPerksMap.getOrDefault(playerId, false)) {
            fromPerksMap.remove(playerId);
            player.closeInventory();
            plugin.getPerksGUI().openMainPage(player);
        }
    }

    // ── Item builders ─────────────────────────────────────────────────────────

    private ItemStack createTunicItem(DyeColor color, boolean isActive) {
        ItemStack tunic = new ItemStack(Material.LEATHER_CHESTPLATE);
        LeatherArmorMeta meta = (LeatherArmorMeta) tunic.getItemMeta();
        if (meta == null) return tunic;

        int rgb = LEATHER_COLORS.getOrDefault(color, 16383998);
        meta.setColor(Color.fromRGB(rgb));
        meta.setDisplayName(colorChatCode(color) + formatColorName(color));
        meta.setLore(List.of("§7Click to set your glow colour."));

        if (isActive) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        tunic.setItemMeta(meta);
        return tunic;
    }

    private ItemStack createBarrierItem(boolean isActive) {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName("§cTurn Off Glowing");
        meta.setLore(List.of("§7Click to disable the glow effect."));

        if (isActive) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(BACK_ITEM_CMD);
            meta.setDisplayName("§c◀ Back to Perks");
            item.setItemMeta(meta);
        }
        return item;
    }

    // ── Utility ───────────────────────────────────────────────────────────────

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

    private String colorChatCode(DyeColor color) {
        return switch (color) {
            case WHITE      -> "§f";
            case ORANGE     -> "§6";
            case MAGENTA    -> "§d";
            case LIGHT_BLUE -> "§b";
            case YELLOW     -> "§e";
            case LIME       -> "§a";
            case PINK       -> "§d";
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

    private static final class GlowingGuiHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
