package collectionlog.debug;

import collectionlog.CollectionLogPlugin;
import collectionlog.utils.ItemSignatureHelper;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listener for debugging item signatures.
 * When a player has debug mode enabled, clicking any item shows its metadata.
 */
public class DebugItemListener implements Listener {
    private static final String ARROW = " \u2192 ";

    private final CollectionLogPlugin plugin;
    private final ItemSignatureHelper signatureHelper;
    private final Set<UUID> debugModePlayers = ConcurrentHashMap.newKeySet();

    public DebugItemListener(CollectionLogPlugin plugin) {
        this.plugin = plugin;
        this.signatureHelper = new ItemSignatureHelper();
    }

    public boolean toggleDebugMode(UUID playerId) {
        if (debugModePlayers.contains(playerId)) {
            debugModePlayers.remove(playerId);
            return false;
        }

        debugModePlayers.add(playerId);
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!debugModePlayers.contains(player.getUniqueId())) {
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType().isAir()) {
            return;
        }

        showItemDebugInfo(player, clickedItem);
    }

    private void showItemDebugInfo(Player player, ItemStack item) {
        String divider = ChatColor.GRAY + "═══════════════════════════════════";

        player.sendMessage(divider);
        player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "ITEM DEBUG INFO");
        player.sendMessage(divider);

        player.sendMessage(ChatColor.YELLOW + "Material: " + ChatColor.WHITE + item.getType().name());
        plugin.getLogger().info("[DEBUG] Material: " + item.getType().name());

        player.sendMessage(ChatColor.YELLOW + "Amount: " + ChatColor.WHITE + item.getAmount());
        plugin.getLogger().info("[DEBUG] Amount: " + item.getAmount());

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            player.sendMessage(ChatColor.RED + "No ItemMeta found!");
            plugin.getLogger().info("[DEBUG] No ItemMeta found!");
            player.sendMessage(divider);
            return;
        }

        if (meta.hasDisplayName()) {
            String rawName = meta.getDisplayName();
            String strippedName = ChatColor.stripColor(rawName);
            player.sendMessage(ChatColor.YELLOW + "Display Name (raw): " + rawName);
            player.sendMessage(ChatColor.YELLOW + "Display Name (stripped): " + ChatColor.WHITE + strippedName);
            plugin.getLogger().info("[DEBUG] Display Name (raw): " + rawName);
            plugin.getLogger().info("[DEBUG] Display Name (stripped): " + strippedName);
        } else {
            player.sendMessage(ChatColor.YELLOW + "Display Name: " + ChatColor.GRAY + "None");
            plugin.getLogger().info("[DEBUG] Display Name: None");
        }

        if (meta.hasCustomModelData()) {
            int cmd = meta.getCustomModelData();
            player.sendMessage(ChatColor.YELLOW + "CustomModelData: " + ChatColor.WHITE + cmd);
            plugin.getLogger().info("[DEBUG] CustomModelData: " + cmd);
        } else {
            player.sendMessage(ChatColor.YELLOW + "CustomModelData: " + ChatColor.GRAY + "None");
            plugin.getLogger().info("[DEBUG] CustomModelData: None");
        }

        List<String> lore = meta.getLore();
        if (meta.hasLore() && lore != null) {
            player.sendMessage(ChatColor.YELLOW + "Lore:");
            plugin.getLogger().info("[DEBUG] Lore:");
            for (String line : lore) {
                player.sendMessage(ChatColor.GRAY + "  - " + line);
                plugin.getLogger().info("[DEBUG]   - " + line);
            }
        } else {
            player.sendMessage(ChatColor.YELLOW + "Lore: " + ChatColor.GRAY + "None");
            plugin.getLogger().info("[DEBUG] Lore: None");
        }

        // PersistentDataContainer
        player.sendMessage(divider);
        player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "PERSISTENT DATA CONTAINER:");
        plugin.getLogger().info("[DEBUG] PersistentDataContainer:");

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Set<NamespacedKey> pdcKeys = pdc.getKeys();

        if (pdcKeys.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "  No persistent data");
            plugin.getLogger().info("[DEBUG]   No persistent data");
        } else {
            for (NamespacedKey key : pdcKeys) {
                String fullKey = key.getNamespace() + ":" + key.getKey();
                String value = getPDCValueAsString(pdc, key);
                player.sendMessage(ChatColor.AQUA + "  " + fullKey + ChatColor.GRAY + ARROW + ChatColor.WHITE + value);
                plugin.getLogger().info("[DEBUG]   " + fullKey + ARROW + value);
            }
        }

        player.sendMessage(divider);
        player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "GENERATED SIGNATURES:");
        plugin.getLogger().info("[DEBUG] Generated Signatures:");

        Set<String> signatures = signatureHelper.generateSignatures(item);
        if (signatures.isEmpty()) {
            player.sendMessage(ChatColor.RED + "  No signatures generated!");
            plugin.getLogger().info("[DEBUG]   No signatures generated!");
        } else {
            for (String signature : signatures) {
                player.sendMessage(ChatColor.GREEN + "  ✓ " + ChatColor.WHITE + signature);
                plugin.getLogger().info("[DEBUG]   ✓ " + signature);
            }
        }

        player.sendMessage(divider);
    }

    /**
     * Attempt to extract a PDC value as a readable string.
     * Tries common data types in order.
     */
    private String getPDCValueAsString(PersistentDataContainer pdc, NamespacedKey key) {
        String stringValue = pdc.get(key, PersistentDataType.STRING);
        if (stringValue != null) {
            return "\"" + stringValue + "\"";
        }

        Integer intValue = pdc.get(key, PersistentDataType.INTEGER);
        if (intValue != null) {
            return String.valueOf(intValue);
        }

        Long longValue = pdc.get(key, PersistentDataType.LONG);
        if (longValue != null) {
            return longValue + "L";
        }

        Double doubleValue = pdc.get(key, PersistentDataType.DOUBLE);
        if (doubleValue != null) {
            return String.valueOf(doubleValue);
        }

        Float floatValue = pdc.get(key, PersistentDataType.FLOAT);
        if (floatValue != null) {
            return floatValue + "f";
        }

        Short shortValue = pdc.get(key, PersistentDataType.SHORT);
        if (shortValue != null) {
            return String.valueOf(shortValue);
        }

        Byte byteValue = pdc.get(key, PersistentDataType.BYTE);
        if (byteValue != null) {
            return String.valueOf(byteValue);
        }

        byte[] byteArray = pdc.get(key, PersistentDataType.BYTE_ARRAY);
        if (byteArray != null) {
            return "[byte array, length=" + byteArray.length + "]";
        }

        int[] intArray = pdc.get(key, PersistentDataType.INTEGER_ARRAY);
        if (intArray != null) {
            return "[int array, length=" + intArray.length + "]";
        }

        long[] longArray = pdc.get(key, PersistentDataType.LONG_ARRAY);
        if (longArray != null) {
            return "[long array, length=" + longArray.length + "]";
        }

        PersistentDataContainer nested = pdc.get(key, PersistentDataType.TAG_CONTAINER);
        if (nested != null) {
            return "[nested container, " + nested.getKeys().size() + " keys]";
        }

        return "<unknown type>";
    }
}
