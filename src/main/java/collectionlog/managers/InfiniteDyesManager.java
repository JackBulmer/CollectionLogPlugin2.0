package collectionlog.managers;

import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.Map;
import java.util.Set;

/**
 * Utility manager for the Infinite Dyes perk.
 *
 * <p>Responsible for determining whether an item is dyeable and producing
 * the dyed variant for a given {@link DyeColor}.
 */
public class InfiniteDyesManager {

    // ── Dyeable base suffixes ─────────────────────────────────────────────────

    /** Material name suffixes shared by all 16 colour variants of the item type. */
    private static final String[] COLORED_SUFFIXES = {
            "_WOOL", "_CARPET", "_CONCRETE", "_CONCRETE_POWDER",
            "_TERRACOTTA",
            "_STAINED_GLASS", "_STAINED_GLASS_PANE",
            "_BED", "_SHULKER_BOX", "_CANDLE", "_BANNER",
            "_HARNESS"
    };

    /**
     * Leather armour pieces – dyed via LeatherArmorMeta, not by material swap.
     */
    private static final Set<Material> LEATHER_ARMOR = Set.of(
            Material.LEATHER_HELMET,
            Material.LEATHER_CHESTPLATE,
            Material.LEATHER_LEGGINGS,
            Material.LEATHER_BOOTS
    );

    /**
     * Uncoloured → coloured suffix mappings for items that do not already
     * carry a colour prefix (e.g. plain GLASS → [COLOR]_STAINED_GLASS).
     */
    private static final Map<Material, String> SPECIAL_MAPPINGS = Map.of(
            Material.GLASS,       "_STAINED_GLASS",
            Material.GLASS_PANE,  "_STAINED_GLASS_PANE",
            Material.CANDLE,      "_CANDLE",
            Material.SHULKER_BOX, "_SHULKER_BOX",
            Material.TERRACOTTA,  "_TERRACOTTA"
    );

    /**
     * Preset RGB colours for leather armour to match dye colour swatches.
     * Values are standard Minecraft leather dye colours.
     */
    private static final Map<DyeColor, Integer> LEATHER_PRESET_COLORS = Map.ofEntries(
            Map.entry(DyeColor.WHITE,      16383998),
            Map.entry(DyeColor.ORANGE,     16351261),
            Map.entry(DyeColor.MAGENTA,    13061821),
            Map.entry(DyeColor.LIGHT_BLUE,  3847130),
            Map.entry(DyeColor.YELLOW,     16701501),
            Map.entry(DyeColor.LIME,        8439583),
            Map.entry(DyeColor.PINK,       15961002),
            Map.entry(DyeColor.GRAY,        4673362),
            Map.entry(DyeColor.LIGHT_GRAY, 10329495),
            Map.entry(DyeColor.CYAN,        1481884),
            Map.entry(DyeColor.PURPLE,      8991416),
            Map.entry(DyeColor.BLUE,        3949738),
            Map.entry(DyeColor.BROWN,       8606770),
            Map.entry(DyeColor.GREEN,       6192150),
            Map.entry(DyeColor.RED,        11546150),
            Map.entry(DyeColor.BLACK,       1908001)
    );

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns true if the item can be dyed using this perk.
     * Banners with patterns still return true – the caller must check
     * {@link #hasBannerPatterns(ItemStack)} separately to provide an
     * appropriate error message.
     */
    public boolean isDyeable(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        return getDyeableSuffix(item.getType()) != null || LEATHER_ARMOR.contains(item.getType());
    }

    /**
     * Returns true if the banner has at least one pattern layer (cannot be dyed).
     */
    public boolean hasBannerPatterns(ItemStack item) {
        if (item == null) return false;
        if (!(item.getItemMeta() instanceof BannerMeta bannerMeta)) return false;
        return !bannerMeta.getPatterns().isEmpty();
    }

    /**
     * Returns a new ItemStack (same amount) dyed to the given colour.
     *
     * <p>Returns {@code null} if the item cannot be dyed or if the target
     * material does not exist in this Minecraft version.
     */
    public ItemStack dyeItem(ItemStack item, DyeColor color) {
        if (item == null || item.getType() == Material.AIR) return null;
        Material type = item.getType();

        // Leather armour – apply RGB preset colour
        if (LEATHER_ARMOR.contains(type)) {
            ItemStack result = item.clone();
            if (result.getItemMeta() instanceof LeatherArmorMeta meta) {
                meta.setColor(getPresetLeatherColor(color));
                result.setItemMeta(meta);
            }
            return result;
        }

        // All other dyeable items – swap material
        String suffix = getDyeableSuffix(type);
        if (suffix == null) return null;

        String newMaterialName = color.name() + suffix;
        Material newMaterial = Material.getMaterial(newMaterialName);
        if (newMaterial == null) return null;

        return new ItemStack(newMaterial, item.getAmount());
    }

    /**
     * Returns the preset {@link Color} for leather armour with the given dye colour.
     */
    public Color getPresetLeatherColor(DyeColor color) {
        int rgb = LEATHER_PRESET_COLORS.getOrDefault(color, 16383998);
        return Color.fromRGB(rgb);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns the colour-prefix suffix for a dyeable material, or {@code null}
     * if the material is not dyeable by this perk.
     *
     * <p>For example {@code WHITE_WOOL} → {@code "_WOOL"}; {@code GLASS} →
     * {@code "_STAINED_GLASS"}.
     */
    String getDyeableSuffix(Material material) {
        // Reject glazed terracotta
        if (material.name().contains("GLAZED")) return null;

        // Plain undyed items that map to a different suffix
        String special = SPECIAL_MAPPINGS.get(material);
        if (special != null) return special;

        String name = material.name();
        for (String suffix : COLORED_SUFFIXES) {
            if (name.endsWith(suffix)) {
                return suffix;
            }
        }
        return null;
    }
}
