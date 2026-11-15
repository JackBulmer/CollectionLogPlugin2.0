package collectionlog.utils;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Helper for applying a Fortune-enchantment bonus to wheat drops.
 *
 * <p>Vanilla Minecraft's Fortune enchantment only boosts the number of wheat seeds
 * dropped by a fully grown wheat crop; the single wheat item itself is always 1
 * regardless of Fortune level. This class rolls an additional, independent bonus for
 * the wheat item count, using the same "uniform bonus count" style formula vanilla
 * uses elsewhere (extra = random(0, fortuneLevel)), so the vanilla seed bonus is left
 * completely untouched.
 */
public final class FortuneCropUtil {

    private FortuneCropUtil() {
    }

    /**
     * Rolls a Fortune-based bonus wheat count for the given tool.
     *
     * @param tool the tool used to break the crop (may be {@code null} or lack Fortune)
     * @return the number of bonus wheat items to award, or 0 if the tool has no Fortune
     */
    public static int rollFortuneBonusWheat(ItemStack tool) {
        if (tool == null) {
            return 0;
        }

        int level = tool.getEnchantmentLevel(Enchantment.FORTUNE);
        if (level <= 0) {
            return 0;
        }

        return ThreadLocalRandom.current().nextInt(level + 1);
    }
}
