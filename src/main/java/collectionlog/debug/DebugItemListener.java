package collectionlog.debug;

import collectionlog.CollectionLogPlugin;
import collectionlog.utils.ItemSignatureHelper;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listener for debugging item signatures.
 * When a player has debug mode enabled, clicking any item shows its metadata.
 */
public class DebugItemListener implements Listener {
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
}
