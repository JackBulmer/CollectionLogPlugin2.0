package collectionlog.utils;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MusicInstrumentMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.MusicInstrument;
import org.bukkit.potion.PotionType;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Generates collection signatures for all supported item formats.
 */
public class ItemSignatureHelper {
    /**
     * Generate all candidate signatures for an item.
     */
    public Set<String> generateSignatures(ItemStack item) {
        return generateSignatures(item, null);
    }

    /**
     * Generate all candidate signatures for an item, with optional source entity context.
     */
    public Set<String> generateSignatures(ItemStack item, EntityType sourceEntityType) {
        Set<String> signatures = new LinkedHashSet<>();
        if (item == null || item.getType().isAir()) {
            return signatures;
        }

        Material type = item.getType();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            signatures.add(type.name());
            addSmithingTemplateSignatures(type, signatures);
            return signatures;
        }

        if (meta.hasCustomModelData()) {
            addCustomModelDataSignatures(meta, signatures);
            return signatures;
        }

        signatures.add(type.name());
        addSmithingTemplateSignatures(type, signatures);
        addEnchantmentSignatures(meta, signatures);
        addPotionSignatures(type, meta, signatures);
        addGoatHornSignatures(type, meta, signatures);
        addMobHeadSignatures(type, sourceEntityType, signatures);

        return signatures;
    }

    private void addEnchantmentSignatures(ItemMeta meta, Set<String> signatures) {
        if (!(meta instanceof EnchantmentStorageMeta enchantmentMeta)) {
            return;
        }

        enchantmentMeta.getStoredEnchants().forEach((enchantment, level) -> {
            String enchantmentKey = enchantment.getKey().getKey().toLowerCase(Locale.ROOT);
            signatures.add(enchantmentKey);
            signatures.add(enchantmentKey + ":" + Math.max(1, level));

            // Backward-compatible variant for older stored progress styles.
            signatures.add(enchantmentKey + "_lvl_" + Math.max(1, level));
            signatures.add((enchantmentKey + "_lvl_" + Math.max(1, level)).toUpperCase(Locale.ROOT));
        });
    }

    private void addPotionSignatures(Material type, ItemMeta meta, Set<String> signatures) {
        if (!(meta instanceof PotionMeta potionMeta)) {
            return;
        }

        PotionType baseType = potionMeta.getBasePotionType();
        if (baseType == null) {
            return;
        }

        String potionName = normalizePotionType(baseType);
        String format = switch (type) {
            case SPLASH_POTION -> "SPLASH";
            case LINGERING_POTION -> "LINGERING";
            default -> "NORMAL";
        };

        signatures.add(potionName + ":" + format);
    }

    private void addSmithingTemplateSignatures(Material type, Set<String> signatures) {
        if (!type.name().endsWith("_ARMOR_TRIM_SMITHING_TEMPLATE")) {
            return;
        }
        signatures.add(type.name()
                .replace("_ARMOR_TRIM_SMITHING_TEMPLATE", "")
                .toLowerCase(Locale.ROOT));
    }

    private void addGoatHornSignatures(Material type, ItemMeta meta, Set<String> signatures) {
        if (type != Material.GOAT_HORN || !(meta instanceof MusicInstrumentMeta instrumentMeta)) {
            return;
        }

        MusicInstrument instrument = instrumentMeta.getInstrument();
        if (instrument != null) {
            signatures.add(instrument.getKey().getKey().toLowerCase(Locale.ROOT));
        }
    }

    private void addMobHeadSignatures(Material type, EntityType sourceEntityType, Set<String> signatures) {
        String materialEntitySignature = switch (type) {
            case ZOMBIE_HEAD -> EntityType.ZOMBIE.name();
            case SKELETON_SKULL -> EntityType.SKELETON.name();
            case CREEPER_HEAD -> EntityType.CREEPER.name();
            case WITHER_SKELETON_SKULL -> EntityType.WITHER_SKELETON.name();
            case DRAGON_HEAD -> EntityType.ENDER_DRAGON.name();
            case PIGLIN_HEAD -> EntityType.PIGLIN.name();
            default -> null;
        };

        if (materialEntitySignature != null) {
            signatures.add(materialEntitySignature);
        }

        if (sourceEntityType != null && type == Material.PLAYER_HEAD) {
            signatures.add(sourceEntityType.name());
        }
    }

    private void addCustomModelDataSignatures(ItemMeta meta, Set<String> signatures) {
        if (!meta.hasCustomModelData()) {
            return;
        }

        int customModelData = meta.getCustomModelData();
        if (meta.hasDisplayName()) {
            String displayName = ChatColor.stripColor(meta.getDisplayName());
            if (displayName != null) {
                displayName = displayName.trim().replaceAll("\\s+", " ");
            }
            if (displayName != null && !displayName.isBlank()) {
                signatures.add(displayName + ":" + customModelData);
            }
        }
    }

    private String normalizePotionType(PotionType potionType) {
        String key = potionType.name()
                .replace("LONG_", "")
                .replace("STRONG_", "");

        return switch (key) {
            case "SWIFTNESS" -> "SPEED";
            case "LEAPING" -> "JUMP_BOOST";
            case "INSTANT_HEAL" -> "HEALING";
            case "INSTANT_DAMAGE" -> "HARMING";
            default -> key;
        };
    }
}
