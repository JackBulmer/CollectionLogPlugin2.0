package collectionlog.listeners;

import collectionlog.CollectionLogPlugin;
import collectionlog.database.dao.BlockOwnershipDAO;
import collectionlog.perks.PerkManager;
import collectionlog.utils.GriefPreventionUtil;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Listener for the Instant Glass Smelting perk.
 * When the perk is active, breaking sand with a shovel drops glass instead of sand.
 *
 * <ul>
 *   <li>Sand  → Glass (regular glass)</li>
 *   <li>Red Sand → Glass (regular glass, not red stained glass)</li>
 * </ul>
 *
 * <p>Requirements enforced here:
 * <ul>
 *   <li>Player must have the perk unlocked <em>and</em> enabled.</li>
 *   <li>Player must be using a shovel (any tier).</li>
 *   <li>Player must not be in Creative mode.</li>
 *   <li>Block must not be player-placed (not in {@link BlockOwnershipDAO}).</li>
 *   <li>Player must have GriefPrevention break permission for the location.</li>
 * </ul>
 */
public class InstantGlassSmeltingListener implements Listener {

    private static final String PERK_ID = "instant_glass_smelting";

    private final PerkManager perkManager;
    private final BlockOwnershipDAO blockOwnershipDAO;

    public InstantGlassSmeltingListener(CollectionLogPlugin plugin) {
        this.perkManager = plugin.getPerkManager();
        this.blockOwnershipDAO = plugin.getBlockOwnershipDAO();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material blockType = block.getType();

        // Only process sand blocks
        if (blockType != Material.SAND && blockType != Material.RED_SAND) {
            return;
        }

        // Creative mode players don't get perk benefits
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        // Check if player has perk unlocked and enabled
        UUID playerId = player.getUniqueId();
        if (!perkManager.hasUnlocked(playerId, PERK_ID) || !perkManager.isEnabled(playerId, PERK_ID)) {
            return;
        }

        // Must use a shovel
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!isShovel(tool.getType())) {
            return;
        }

        // Check if block is player-placed (skip if owned)
        if (blockOwnershipDAO.getOwner(block.getLocation()) != null) {
            return;
        }

        // Check GriefPrevention permissions
        if (!GriefPreventionUtil.canBreak(player, block.getLocation(), blockType)) {
            return;
        }

        // Convert sand to glass
        event.setDropItems(false);
        // No XP reward for smelting (as per requirements).
        block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(Material.GLASS, 1));
    }

    /**
     * Check if sand at the given block should be converted to glass for this player.
     * This method is intended to be called by other perks (e.g. Tunnel Miner) to
     * coordinate behaviour when multiple perks are active simultaneously.
     *
     * @param player the player breaking the block
     * @param block  the sand block being broken
     * @return {@code true} if sand should become glass
     */
    public boolean shouldConvertToGlass(Player player, Block block) {
        Material blockType = block.getType();

        if (blockType != Material.SAND && blockType != Material.RED_SAND) {
            return false;
        }

        UUID playerId = player.getUniqueId();
        if (!perkManager.hasUnlocked(playerId, PERK_ID) || !perkManager.isEnabled(playerId, PERK_ID)) {
            return false;
        }

        if (blockOwnershipDAO.getOwner(block.getLocation()) != null) {
            return false;
        }

        return GriefPreventionUtil.canBreak(player, block.getLocation(), blockType);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isShovel(Material material) {
        return switch (material) {
            case WOODEN_SHOVEL, STONE_SHOVEL, IRON_SHOVEL,
                 GOLDEN_SHOVEL, DIAMOND_SHOVEL, NETHERITE_SHOVEL -> true;
            default -> false;
        };
    }
}
