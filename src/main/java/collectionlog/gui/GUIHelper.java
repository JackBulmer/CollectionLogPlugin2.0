package collectionlog.gui;

import collectionlog.collections.CollectionDefinition;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

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
        return createCollectionIcon(collectionId, collection, collected, total, material);
    }

    public static ItemStack createCollectionIcon(String collectionId, CollectionDefinition collection, int collected, int total, Material material) {
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

    public static ItemStack createItemDisplay(String itemSignature, boolean collected, String collectionName, String collectionId) {
        ItemStack item = createDisplayBaseItem(itemSignature, collectionId);
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

        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
    }

    private static ItemStack createDisplayBaseItem(String itemSignature, String collectionId) {
        String normalizedCollection = normalizeKey(collectionId);
        return switch (normalizedCollection) {
            case "potions" -> createPotionItem(itemSignature);
            case "armourtrims", "armortrims" -> createArmorTrimItem(itemSignature);
            case "jbfishingplus" -> createCustomModelItem(itemSignature, Material.COOKED_COD);
            case "tcg" -> createCustomModelItem(itemSignature, Material.PAPER);
            case "mobheads" -> createMobHeadItem(itemSignature);
            case "enchantments" -> createEnchantmentBookItem(itemSignature);
            default -> new ItemStack(resolveItemMaterial(itemSignature));
        };
    }

    private static ItemStack createPotionItem(String signature) {
        String[] parts = signature.split(":", 2);
        if (parts.length != 2) {
            return new ItemStack(Material.POTION);
        }

        Material potionMaterial = switch (parts[1].trim().toUpperCase(Locale.ROOT)) {
            case "SPLASH" -> Material.SPLASH_POTION;
            case "LINGERING" -> Material.LINGERING_POTION;
            default -> Material.POTION;
        };

        ItemStack potionItem = new ItemStack(potionMaterial);
        ItemMeta rawMeta = potionItem.getItemMeta();
        if (!(rawMeta instanceof PotionMeta potionMeta)) {
            return potionItem;
        }

        PotionType potionType = mapPotionType(parts[0].trim());
        if (potionType != null) {
            potionMeta.setBasePotionType(potionType);
        }
        potionMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        potionItem.setItemMeta(potionMeta);
        return potionItem;
    }

    private static ItemStack createArmorTrimItem(String signature) {
        String patternKey = signature.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        String materialName = patternKey + "_ARMOR_TRIM_SMITHING_TEMPLATE";
        Material templateMaterial = Material.getMaterial(materialName);

        if (templateMaterial == null) {
            templateMaterial = Material.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE;
        }

        return new ItemStack(templateMaterial);
    }

    private static ItemStack createCustomModelItem(String signature, Material baseMaterial) {
        ItemStack stack = new ItemStack(baseMaterial);
        String[] parts = signature.split(":", 2);
        if (parts.length != 2 || !parts[1].trim().chars().allMatch(Character::isDigit)) {
            return stack;
        }

        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }

        meta.setCustomModelData(Integer.parseInt(parts[1].trim()));
        stack.setItemMeta(meta);
        return stack;
    }

    private static ItemStack createMobHeadItem(String signature) {
        String[] parts = signature.split(":", 2);
        if (parts.length == 1) {
            Material vanillaHead = mapVanillaHeadMaterial(parts[0].trim());
            return new ItemStack(vanillaHead);
        }

        String headId = parts[1].trim();
        if (headId.chars().allMatch(Character::isDigit)) {
            ItemStack customHead = tryLoadHeadDatabaseHead(headId);
            if (customHead != null) {
                return customHead;
            }
        }

        return new ItemStack(Material.PLAYER_HEAD);
    }

    private static ItemStack createEnchantmentBookItem(String signature) {
        ItemStack enchantedBook = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta rawMeta = enchantedBook.getItemMeta();
        if (!(rawMeta instanceof EnchantmentStorageMeta meta)) {
            return enchantedBook;
        }

        Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(signature.toLowerCase(Locale.ROOT)));
        if (enchantment != null) {
            meta.addStoredEnchant(enchantment, 1, true);
        }
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        enchantedBook.setItemMeta(meta);
        return enchantedBook;
    }

    private static ItemStack tryLoadHeadDatabaseHead(String headId) {
        try {
            Class<?> apiClass = Class.forName("me.arcaniax.hdb.api.HeadDatabaseAPI");
            Object api = apiClass.getDeclaredConstructor().newInstance();
            Object result = apiClass.getMethod("getItemHead", String.class).invoke(api, headId);
            if (result instanceof ItemStack head && head.getType() == Material.PLAYER_HEAD) {
                return head;
            }
        } catch (ReflectiveOperationException ignored) {
            // HeadDatabase not installed or API unavailable
        }
        return null;
    }

    private static PotionType mapPotionType(String potionName) {
        String normalized = potionName.trim().toUpperCase(Locale.ROOT);
        normalized = switch (normalized) {
            case "SPEED" -> "SWIFTNESS";
            case "JUMP_BOOST" -> "LEAPING";
            case "HEALING" -> "INSTANT_HEAL";
            case "HARMING" -> "INSTANT_DAMAGE";
            default -> normalized;
        };

        try {
            return PotionType.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static Material mapVanillaHeadMaterial(String entityKey) {
        return switch (entityKey.trim().toUpperCase(Locale.ROOT)) {
            case "ZOMBIE" -> Material.ZOMBIE_HEAD;
            case "SKELETON" -> Material.SKELETON_SKULL;
            case "CREEPER" -> Material.CREEPER_HEAD;
            case "WITHER_SKELETON" -> Material.WITHER_SKELETON_SKULL;
            case "ENDER_DRAGON" -> Material.DRAGON_HEAD;
            case "PIGLIN" -> Material.PIGLIN_HEAD;
            default -> Material.PLAYER_HEAD;
        };
    }

    private static String normalizeKey(String input) {
        if (input == null) {
            return "";
        }
        return input.toLowerCase(Locale.ROOT).replace("_", "").replace(" ", "");
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
        if (Enchantment.getByKey(NamespacedKey.minecraft(head.toLowerCase(Locale.ROOT))) != null) {
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
