package collectionlog.utils;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Shared helper for applying a Fortune bonus to wheat yields.
 *
 * <p>Vanilla Minecraft only applies Fortune to the bonus wheat <em>seed</em> drop when
 * breaking a fully grown wheat crop – the wheat item itself always drops exactly 1,
 * regardless of the tool's Fortune level. This utility adds an additional, purely
 * additive bonus to the wheat item count (on top of the unchanged vanilla seed bonus)
 * so that harvesting with a higher Fortune hoe yields more wheat as well.</p>
 */
public final class FortuneCropUtil {

    private FortuneCropUtil() {
    }

    /**
     * Returns the Fortune enchantment level on the given tool, or 0 if the tool is
     * {@code null} or has no Fortune enchantment.
     */
    public static int getFortuneLevel(ItemStack tool) {
        if (tool == null) {
            return 0;
        }
        return tool.getEnchantmentLevel(Enchantment.FORTUNE);
    }

    /**
     * Rolls a bonus wheat amount for a given Fortune level, using the same uniform
     * bonus distribution vanilla uses for Fortune on ores: a random value between
     * 0 and {@code fortuneLevel} (inclusive).
     *
     * @param fortuneLevel the Fortune enchantment level of the harvesting tool
     * @return the number of bonus wheat items to add on top of the base/vanilla drops
     */
    public static int rollBonusWheat(int fortuneLevel) {
        if (fortuneLevel <= 0) {
            return 0;
        }
        return ThreadLocalRandom.current().nextInt(fortuneLevel + 1);
    }
}
