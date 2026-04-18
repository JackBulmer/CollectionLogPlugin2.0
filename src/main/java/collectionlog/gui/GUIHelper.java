package collectionlog.gui;

import collectionlog.collections.CollectionDefinition;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Utility helpers for collection GUI item creation.
 */
public final class GUIHelper {
    private static final int TCG_ICON_CMD = 630001;

    private GUIHelper() {
    }

    public static ItemStack createCollectionIcon(String collectionId, CollectionDefinition collection, int collected, int total) {
        Material material = resolveCollectionMaterial(collectionId, collection);
        ItemStack icon = new ItemStack(material);
        ItemMeta meta = icon.getItemMeta();
        if (meta == null) {
            return icon;
        }

        if ("tcg".equalsIgnoreCase(collectionId)) {
            meta.setCustomModelData(TCG_ICON_CMD);
        }

        double progress = total <= 0 ? 0.0 : (collected * 100.0) / total;
        meta.setDisplayName(ChatColor.AQUA + collection.getName() + ChatColor.GRAY + " (" + collected + "/" + total + ")");

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Progress: " + getProgressColor(progress) + String.format(Locale.ROOT, "%.2f%%", progress));
        lore.add(formatProgressBar(progress));
        lore.add(ChatColor.DARK_GRAY + "Click to view details");
        meta.setLore(lore);
        icon.setItemMeta(meta);

        if (progress >= 100.0) {
            applyGlow(icon);
        }

        return icon;
    }

    public static ItemStack createItemDisplay(String itemSignature, boolean collected, String collectionName) {
        Material material = resolveItemMaterial(itemSignature);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        String displayName = formatSignatureName(itemSignature);
        meta.setDisplayName((collected ? ChatColor.GREEN : ChatColor.RED) + displayName);

        List<String> lore = new ArrayList<>();
        lore.add(collected
                ? ChatColor.GREEN + "Collected \u2713"
                : ChatColor.RED + "Not collected \u2717");
        lore.add(ChatColor.GRAY + "Collection: " + ChatColor.AQUA + collectionName);
        lore.add(ChatColor.DARK_GRAY + "Signature: " + itemSignature);
        meta.setLore(lore);
        item.setItemMeta(meta);

        if (collected) {
            applyGlow(item);
        }

        return item;
    }

    public static ItemStack createNavigationButton(String name, int customModelData) {
        ItemStack button = new ItemStack(Material.PAPER);
        ItemMeta meta = button.getItemMeta();
        if (meta == null) {
            return button;
        }

        meta.setDisplayName(ChatColor.GOLD + name);
        meta.setCustomModelData(customModelData);
        button.setItemMeta(meta);
        return button;
    }

    public static ItemStack createFilterButton(boolean showUncollectedOnly) {
        ItemStack button = new ItemStack(showUncollectedOnly ? Material.PAPER : Material.HOPPER);
        ItemMeta meta = button.getItemMeta();
        if (meta == null) {
            return button;
        }

        if (showUncollectedOnly) {
            meta.setCustomModelData(100001);
            meta.setDisplayName(ChatColor.GOLD + "Filter: Uncollected Only");
            meta.setLore(List.of(ChatColor.GRAY + "Click to show all items"));
        } else {
            meta.setDisplayName(ChatColor.GOLD + "Filter: Showing All");
            meta.setLore(List.of(ChatColor.GRAY + "Click to show only uncollected items"));
        }

        button.setItemMeta(meta);
        return button;
    }

    public static String formatProgressBar(double percentage) {
        int bars = 10;
        int filled = (int) Math.round(Math.max(0.0, Math.min(100.0, percentage)) / 100.0 * bars);
        return ChatColor.GREEN + "⬛".repeat(filled) + ChatColor.DARK_GRAY + "⬜".repeat(Math.max(0, bars - filled));
    }

    public static void applyGlow(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        meta.addEnchant(Enchantment.DURABILITY, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
    }

    private static Material resolveCollectionMaterial(String collectionId, CollectionDefinition collection) {
        if ("tcg".equalsIgnoreCase(collectionId)) {
            return Material.PAPER;
        }

        for (String signature : collection.getItems()) {
            Material candidate = resolveItemMaterial(signature);
            if (candidate != Material.PAPER) {
                return candidate;
            }
        }

        return Material.CHEST;
    }

    private static Material resolveItemMaterial(String signature) {
        if (signature == null || signature.isBlank()) {
            return Material.PAPER;
        }

        String[] parts = signature.split(":", 2);
        String head = parts[0].trim();

        Material asMaterial = Material.matchMaterial(head);
        if (asMaterial != null) {
            return asMaterial;
        }

        if (parts.length == 2) {
            String tail = parts[1].trim();
            if (tail.chars().allMatch(Character::isDigit)) {
                return Material.PAPER;
            }

            if ("SPLASH".equalsIgnoreCase(tail)) {
                return Material.SPLASH_POTION;
            }
            if ("LINGERING".equalsIgnoreCase(tail)) {
                return Material.LINGERING_POTION;
            }
            if ("NORMAL".equalsIgnoreCase(tail)) {
                return Material.POTION;
            }

            return Material.PLAYER_HEAD;
        }

        String normalized = head.toUpperCase(Locale.ROOT);
        if (normalized.contains("CURSE") || normalized.contains("PROTECTION") || normalized.contains("SHARPNESS")) {
            return Material.ENCHANTED_BOOK;
        }
        if (normalized.contains("HORN")) {
            return Material.GOAT_HORN;
        }

        return Material.PAPER;
    }

    private static String formatSignatureName(String signature) {
        if (signature == null || signature.isBlank()) {
            return "Unknown";
        }

        if (signature.contains(":")) {
            String[] parts = signature.split(":", 2);
            String left = formatWords(parts[0]);
            String right = parts[1];

            if (right.chars().allMatch(Character::isDigit)) {
                return left;
            }

            return left + " (" + formatWords(right) + ")";
        }

        return formatWords(signature);
    }

    private static String formatWords(String input) {
        String normalized = input
                .replace('_', ' ')
                .trim()
                .replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            return "Unknown";
        }

        String[] words = normalized.split(" ");
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            if (word.isEmpty()) {
                continue;
            }
            builder.append(word.substring(0, 1).toUpperCase(Locale.ROOT));
            if (word.length() > 1) {
                builder.append(word.substring(1).toLowerCase(Locale.ROOT));
            }
            if (i < words.length - 1) {
                builder.append(' ');
            }
        }
        return builder.toString();
    }

    private static ChatColor getProgressColor(double progress) {
        if (progress >= 100.0) {
            return ChatColor.GREEN;
        }
        if (progress >= 50.0) {
            return ChatColor.YELLOW;
        }
        return ChatColor.RED;
    }
}
