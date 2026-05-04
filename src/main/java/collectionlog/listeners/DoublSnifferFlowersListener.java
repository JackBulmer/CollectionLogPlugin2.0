package collectionlog.listeners;

import collectionlog.CollectionLogPlugin;
import collectionlog.perks.PerkManager;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Listener for the Double Sniffer Flowers perk.
 * When a player with this perk harvests fully grown sniffer flowers from farmland,
 * they receive both the flower item and a seed.
 */
public class DoublSnifferFlowersListener implements Listener {

    private static final String PERK_ID = "double_sniffer_flowers";

    private final CollectionLogPlugin plugin;
    private final PerkManager perkManager;

    public DoublSnifferFlowersListener(CollectionLogPlugin plugin) {
        this.plugin = plugin;
        this.perkManager = plugin.getPerkManager();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material blockType = block.getType();

        // Only process sniffer flowers
        if (blockType != Material.TORCHFLOWER_CROP && blockType != Material.PITCHER_CROP) {
            return;
        }

        // Creative mode excluded (no drops)
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        UUID playerId = player.getUniqueId();

        // Check perk unlocked
        if (!perkManager.hasUnlocked(playerId, PERK_ID)) {
            return;
        }

        // Must be fully grown
        BlockData blockData = block.getBlockData();
        if (!(blockData instanceof Ageable ageable)) {
            return;
        }

        if (ageable.getAge() != ageable.getMaximumAge()) {
            return; // Not fully grown
        }

        // Must be on farmland
        Block below = block.getRelative(0, -1, 0);
        if (below.getType() != Material.FARMLAND) {
            return; // Not on farmland (might be in flower pot or on dirt)
        }

        // Cancel vanilla drops - we'll handle them manually
        event.setDropItems(false);

        // Determine flower and seed types
        ItemStack flower;
        ItemStack seed;

        if (blockType == Material.TORCHFLOWER_CROP) {
            flower = new ItemStack(Material.TORCHFLOWER, 1);
            seed = new ItemStack(Material.TORCHFLOWER_SEEDS, 1);
        } else { // PITCHER_CROP
            flower = new ItemStack(Material.PITCHER_PLANT, 1);
            seed = new ItemStack(Material.PITCHER_POD, 1);
        }

        // Drop flower and seed at block location
        block.getWorld().dropItemNaturally(block.getLocation(), flower);
        block.getWorld().dropItemNaturally(block.getLocation(), seed);

        // Set block to air (break the crop)
        block.setType(Material.AIR);
    }
}
