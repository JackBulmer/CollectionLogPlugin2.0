package collectionlog.utils;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MusicInstrumentMeta;
import org.bukkit.inventory.meta.PotionMeta;
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
        signatures.add(type.name());

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return signatures;
        }

        addEnchantmentSignatures(meta, signatures);
        addPotionSignatures(type, meta, signatures);
        addArmourTrimSignatures(meta, signatures);
        addGoatHornSignatures(type, meta, signatures);
        addMobHeadSignatures(type, sourceEntityType, signatures);
        addCustomModelDataSignatures(meta, signatures);

        return signatures;
    }

    private void addEnchantmentSignatures(ItemMeta meta, Set<String> signatures) {
        if (!(meta instanceof EnchantmentStorageMeta enchantmentMeta)) {
            return;
        }

        enchantmentMeta.getStoredEnchants().forEach((enchantment, level) ->
                signatures.add(enchantment.getKey().getKey().toLowerCase(Locale.ROOT)));
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

    private void addArmourTrimSignatures(ItemMeta meta, Set<String> signatures) {
        if (!(meta instanceof ArmorMeta armorMeta)) {
            return;
        }

        if (armorMeta.hasTrim() && armorMeta.getTrim() != null) {
            signatures.add(armorMeta.getTrim().getPattern().getKey().getKey().toLowerCase(Locale.ROOT));
        }
    }

    private void addGoatHornSignatures(Material type, ItemMeta meta, Set<String> signatures) {
        if (type != Material.GOAT_HORN || !(meta instanceof MusicInstrumentMeta instrumentMeta)) {
            return;
        }

        if (instrumentMeta.hasInstrument()) {
            signatures.add(instrumentMeta.getInstrument().getKey().getKey().toLowerCase(Locale.ROOT));
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
