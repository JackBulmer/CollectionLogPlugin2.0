package collectionlog.teleport.gui;

import collectionlog.CollectionLogPlugin;
import collectionlog.teleport.HomeManager;
import collectionlog.teleport.TeleportManager;
import collectionlog.teleport.TeleportSettingsManager;
import collectionlog.teleport.TeleportValidator;
import collectionlog.teleport.data.Home;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class HomesGUI implements Listener {
    private static final int INVENTORY_SIZE = 27;
    private static final int BACK_SLOT = 22;
    private static final int BACK_ICON_CMD = 24001;

    private final CollectionLogPlugin plugin;
    private final HomeManager homeManager;
    private final TeleportManager teleportManager;
    private final TeleportSettingsManager settingsManager;
    private final TeleportValidator validator;

    public HomesGUI(CollectionLogPlugin plugin, HomeManager homeManager, TeleportManager teleportManager,
                    TeleportSettingsManager settingsManager, TeleportValidator validator) {
        this.plugin = plugin;
        this.homeManager = homeManager;
        this.teleportManager = teleportManager;
        this.settingsManager = settingsManager;
        this.validator = validator;
    }

    public void open(Player player) {
        homeManager.getHomes(player.getUniqueId()).thenAccept(homes ->
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Inventory inv = Bukkit.createInventory(new HomesGUIHolder(), INVENTORY_SIZE, "§8✦ Homes");

                for (int i = 0; i < Math.min(homes.size(), 18); i++) {
                    inv.setItem(i, createHomeItem(homes.get(i)));
                }

                ItemStack back = new ItemStack(Material.PAPER);
                ItemMeta backMeta = back.getItemMeta();
                if (backMeta != null) {
                    backMeta.setDisplayName("§cBack");
                    backMeta.setCustomModelData(BACK_ICON_CMD);
                    back.setItemMeta(backMeta);
                }
                inv.setItem(BACK_SLOT, back);

                player.openInventory(inv);
            })
        );
    }

    private ItemStack createHomeItem(Home home) {
        ItemStack item = new ItemStack(home.getMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String dimColor = getDimensionColor(home.getWorld());
        String dimName = getDimensionName(home.getWorld());

        meta.setDisplayName(dimColor + home.getName());

        List<String> lore = new ArrayList<>();
        lore.add("§7x: " + (int) home.getX() + " y: " + (int) home.getY() + " z: " + (int) home.getZ());
        lore.add(dimColor + dimName);
        lore.add("§eClick to Teleport!");
        meta.setLore(lore);

        item.setItemMeta(meta);
        return item;
    }

    private String getDimensionColor(String worldName) {
        if (worldName.contains("nether")) return "§c";
        if (worldName.contains("end")) return "§5";
        return "§a";
    }

    private String getDimensionName(String worldName) {
        if (worldName.contains("nether")) return "Nether";
        if (worldName.contains("end")) return "End";
        return "Overworld";
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof HomesGUIHolder)) return;

        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= INVENTORY_SIZE) return;

        if (slot == BACK_SLOT) {
            player.closeInventory();
            return;
        }

        if (slot < 18) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;
            ItemMeta meta = clicked.getItemMeta();
            if (meta == null || !meta.hasDisplayName()) return;

            String displayName = meta.getDisplayName();
            // Strip leading §X color prefix if present
            String homeName = (displayName.length() > 2 && displayName.charAt(0) == '§')
                ? displayName.substring(2)
                : displayName;

            player.closeInventory();
            plugin.getServer().dispatchCommand(player, "home " + homeName);
        }
    }

    public static class HomesGUIHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() { return null; }
    }
}
