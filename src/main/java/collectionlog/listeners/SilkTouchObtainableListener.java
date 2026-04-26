package collectionlog.listeners;

import collectionlog.CollectionLogPlugin;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;

/**
 * Makes normally unobtainable blocks obtainable with Silk Touch.
 * Add materials to the SILK_TOUCH_OBTAINABLE set to make them drop when broken with Silk Touch.
 */
public class SilkTouchObtainableListener implements Listener {

    private final CollectionLogPlugin plugin;

    /**
     * Add any Material here to make it obtainable with Silk Touch.
     * These blocks will drop themselves when broken with a Silk Touch tool.
     */
    private static final Set<Material> SILK_TOUCH_OBTAINABLE = new HashSet<>(Set.of(
            Material.BUDDING_AMETHYST,
            Material.REINFORCED_DEEPSLATE,
            Material.DIRT_PATH
    ));

    public SilkTouchObtainableListener(CollectionLogPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Material blockType = block.getType();

        if (!SILK_TOUCH_OBTAINABLE.contains(blockType)) {
            return;
        }

        Player player = event.getPlayer();

        if (player.getGameMode() != GameMode.SURVIVAL) {
            return;
        }

        ItemStack tool = player.getInventory().getItemInMainHand();

        if (tool == null || !tool.containsEnchantment(Enchantment.SILK_TOUCH)) {
            return;
        }

        // Cancel vanilla drop behavior
        event.setDropItems(false);

        // Drop the block itself
        block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(blockType, 1));

        // Set block to air
        block.setType(Material.AIR);

        plugin.getLogger().info("✨ Player " + player.getName() + " obtained " + blockType + " with Silk Touch");
    }

    /**
     * Add a material to the silk touch obtainable list
     */
    public static void addObtainableMaterial(Material material) {
        SILK_TOUCH_OBTAINABLE.add(material);
    }

    /**
     * Remove a material from the silk touch obtainable list
     */
    public static void removeObtainableMaterial(Material material) {
        SILK_TOUCH_OBTAINABLE.remove(material);
    }

    /**
     * Get all silk touch obtainable materials
     */
    public static Set<Material> getObtainableMaterials() {
        return new HashSet<>(SILK_TOUCH_OBTAINABLE);
    }

    /**
     * Check if a material is silk touch obtainable
     */
    public static boolean isObtainable(Material material) {
        return SILK_TOUCH_OBTAINABLE.contains(material);
    }
}
