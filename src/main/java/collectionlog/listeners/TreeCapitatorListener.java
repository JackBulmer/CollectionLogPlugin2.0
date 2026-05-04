package collectionlog.listeners;

import collectionlog.CollectionLogPlugin;
import collectionlog.database.dao.BlockOwnershipDAO;
import collectionlog.perks.PerkManager;
import collectionlog.utils.GriefPreventionUtil;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

/**
 * Listener for the Tree Capitator perk.
 * Allows players to fell entire trees by breaking the bottom log with an axe.
 * Auto-disables after 5 minutes of inactivity.
 */
public class TreeCapitatorListener implements Listener {

    private static final String PERK_ID = "tree_capitator";
    private static final long INACTIVITY_TIMEOUT_MS = 5 * 60 * 1000L; // 5 minutes
    private static final int MAX_TREE_SIZE = 250;
    /** Tree felling costs 1 durability per DURABILITY_DIVISOR logs broken. */
    private static final int DURABILITY_DIVISOR = 2;

    private final CollectionLogPlugin plugin;
    private final PerkManager perkManager;
    private final BlockOwnershipDAO blockOwnershipDAO;

    /** Track last use time per player for auto-disable. */
    private final Map<UUID, Long> lastUseTime = new HashMap<>();

    /** Valid log types (excludes stripped variants). */
    private static final Set<Material> LOG_TYPES = Set.of(
            Material.OAK_LOG,
            Material.BIRCH_LOG,
            Material.SPRUCE_LOG,
            Material.JUNGLE_LOG,
            Material.ACACIA_LOG,
            Material.DARK_OAK_LOG,
            Material.MANGROVE_LOG,
            Material.CHERRY_LOG,
            Material.PALE_OAK_LOG,
            Material.CRIMSON_STEM,
            Material.WARPED_STEM
    );

    /** Nether wart blocks to remove alongside nether trees. */
    private static final Set<Material> NETHER_WART_BLOCKS = Set.of(
            Material.NETHER_WART_BLOCK,
            Material.WARPED_WART_BLOCK
    );

    /** Leaf types used to detect natural (overworld) trees. */
    private static final Set<Material> LEAF_TYPES = Set.of(
            Material.OAK_LEAVES,
            Material.BIRCH_LEAVES,
            Material.SPRUCE_LEAVES,
            Material.JUNGLE_LEAVES,
            Material.ACACIA_LEAVES,
            Material.DARK_OAK_LEAVES,
            Material.MANGROVE_LEAVES,
            Material.CHERRY_LEAVES,
            Material.PALE_OAK_LEAVES,
            Material.AZALEA_LEAVES,
            Material.FLOWERING_AZALEA_LEAVES
    );

    public TreeCapitatorListener(CollectionLogPlugin plugin) {
        this.plugin = plugin;
        this.perkManager = plugin.getPerkManager();
        this.blockOwnershipDAO = plugin.getBlockOwnershipDAO();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material blockType = block.getType();

        // Must be a natural log type
        if (!LOG_TYPES.contains(blockType)) {
            return;
        }

        // Creative mode excluded
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        UUID playerId = player.getUniqueId();

        // Check perk unlocked and enabled
        if (!perkManager.hasUnlocked(playerId, PERK_ID) || !perkManager.isEnabled(playerId, PERK_ID)) {
            return;
        }

        // Must use an axe
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!isAxe(tool.getType())) {
            return;
        }

        // Skip player-placed logs
        if (blockOwnershipDAO.getOwner(block.getLocation()) != null) {
            return;
        }

        // Must be the bottom log of a tree (block below is not a log)
        if (!isBottomLog(block)) {
            return;
        }

        // Check GriefPrevention permissions
        if (!GriefPreventionUtil.canBreak(player, block.getLocation(), blockType)) {
            return;
        }

        // Validate tree size and axe durability BEFORE cancelling the event so that
        // vanilla still handles the break when the perk cannot apply.
        Set<Block> treeLogs = findTreeLogs(block);

        if (treeLogs.size() > MAX_TREE_SIZE) {
            player.sendMessage("§cTree too large to fell! (max " + MAX_TREE_SIZE + " logs)");
            return; // Let vanilla break just the one log
        }

        // Leaf check: overworld trees must have leaves nearby; nether trees are exempt
        if (!isNaturalTree(block, treeLogs)) {
            player.sendMessage("§cOnly natural trees with leaves can be felled!");
            return;
        }

        // Durability cost: 1 per DURABILITY_DIVISOR logs, minimum 1
        int durabilityCost = Math.max(1, treeLogs.size() / DURABILITY_DIVISOR);

        if (!canSurviveDurability(tool, durabilityCost)) {
            player.sendMessage("§cYour axe is too damaged to fell this tree!");
            return; // Let vanilla break just the one log
        }

        // All checks passed — cancel vanilla break and fell the entire tree
        event.setCancelled(true);

        fellTree(player, block, tool, treeLogs, durabilityCost);

        // Update last use time for inactivity tracking
        lastUseTime.put(playerId, System.currentTimeMillis());
    }

    // ── Tree felling ──────────────────────────────────────────────────────────

    /**
     * Breaks all natural logs in the tree and applies axe durability cost.
     * Pre-conditions (tree size, axe durability) are already validated by the caller.
     */
    private void fellTree(Player player, Block startLog, ItemStack axe,
                          Set<Block> treeLogs, int durabilityCost) {

        // Break each log, dropping items with Fortune
        for (Block log : treeLogs) {
            if (!GriefPreventionUtil.canBreak(player, log.getLocation(), log.getType())) {
                continue;
            }

            if (blockOwnershipDAO.getOwner(log.getLocation()) != null) {
                continue;
            }

            Location dropLoc = log.getLocation().add(0.5, 0.5, 0.5);
            Collection<ItemStack> drops = log.getDrops(axe, player);
            for (ItemStack drop : drops) {
                log.getWorld().dropItemNaturally(dropLoc, drop);
            }

            log.setType(Material.AIR);
        }

        // Remove nether wart blocks adjacent to nether trees
        if (isNetherLog(startLog.getType())) {
            removeNetherWartBlocks(treeLogs);
        }

        // Apply total durability cost
        damageAxe(axe, player, durabilityCost);
    }

    /**
     * Flood-fills upward from the starting log to find all connected natural logs
     * of the same type.
     */
    private Set<Block> findTreeLogs(Block startLog) {
        Set<Block> logs = new HashSet<>();
        Queue<Block> toCheck = new LinkedList<>();
        toCheck.add(startLog);
        logs.add(startLog);

        Material logType = startLog.getType();

        while (!toCheck.isEmpty() && logs.size() <= MAX_TREE_SIZE) {
            Block current = toCheck.poll();

            for (int x = -1; x <= 1; x++) {
                for (int y = 0; y <= 1; y++) { // same level and upward only
                    for (int z = -1; z <= 1; z++) {
                        if (x == 0 && y == 0 && z == 0) {
                            continue;
                        }

                        Block adjacent = current.getRelative(x, y, z);

                        if (adjacent.getType() != logType) {
                            continue;
                        }
                        if (logs.contains(adjacent)) {
                            continue;
                        }
                        if (blockOwnershipDAO.getOwner(adjacent.getLocation()) != null) {
                            continue;
                        }

                        logs.add(adjacent);
                        toCheck.add(adjacent);
                    }
                }
            }
        }

        return logs;
    }

    /**
     * Removes all nether wart blocks adjacent to the given set of logs.
     */
    private void removeNetherWartBlocks(Set<Block> treeLogs) {
        Set<Block> wartBlocks = new HashSet<>();

        for (Block log : treeLogs) {
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        if (x == 0 && y == 0 && z == 0) {
                            continue;
                        }
                        Block adjacent = log.getRelative(x, y, z);
                        if (NETHER_WART_BLOCKS.contains(adjacent.getType())) {
                            wartBlocks.add(adjacent);
                        }
                    }
                }
            }
        }

        for (Block wart : wartBlocks) {
            wart.setType(Material.AIR);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns true if this is a natural tree.
     * Nether trees (crimson/warped stems) are always considered natural since there
     * are no nether structures that could be confused with them.
     * Overworld trees must have at least one leaf block within 2 blocks of any log.
     */
    private boolean isNaturalTree(Block startLog, Set<Block> logs) {
        if (isNetherLog(startLog.getType())) {
            return true;
        }
        return hasLeavesNearby(logs);
    }

    /**
     * Returns true if any log in the set has a leaf block within 2 blocks.
     */
    private boolean hasLeavesNearby(Set<Block> logs) {
        for (Block log : logs) {
            for (int x = -2; x <= 2; x++) {
                for (int y = -2; y <= 2; y++) {
                    for (int z = -2; z <= 2; z++) {
                        if (LEAF_TYPES.contains(log.getRelative(x, y, z).getType())) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Returns true if the block below is not a log (i.e. this is the bottom log).
     */
    private boolean isBottomLog(Block block) {
        Block below = block.getRelative(BlockFace.DOWN);
        return !LOG_TYPES.contains(below.getType());
    }

    private boolean isAxe(Material material) {
        return switch (material) {
            case WOODEN_AXE, STONE_AXE, IRON_AXE,
                 GOLDEN_AXE, DIAMOND_AXE, NETHERITE_AXE -> true;
            default -> false;
        };
    }

    private boolean isNetherLog(Material material) {
        return material == Material.CRIMSON_STEM || material == Material.WARPED_STEM;
    }

    /**
     * Returns true if the axe can absorb {@code cost} damage without breaking.
     */
    private boolean canSurviveDurability(ItemStack axe, int cost) {
        if (axe.getType().getMaxDurability() == 0) {
            return true; // Unbreakable items
        }
        ItemMeta meta = axe.getItemMeta();
        if (meta instanceof Damageable damageable) {
            int currentDamage = damageable.getDamage();
            return (currentDamage + cost) < axe.getType().getMaxDurability();
        }
        return true;
    }

    /**
     * Applies a bulk durability cost to the axe and breaks it if durability is exhausted.
     */
    private void damageAxe(ItemStack axe, Player player, int damage) {
        if (axe.getType().getMaxDurability() == 0 || damage <= 0) {
            return;
        }

        ItemMeta meta = axe.getItemMeta();
        if (meta instanceof Damageable damageable) {
            int newDamage = damageable.getDamage() + damage;
            if (newDamage >= axe.getType().getMaxDurability()) {
                player.getInventory().setItemInMainHand(null);
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            } else {
                damageable.setDamage(newDamage);
                axe.setItemMeta(meta);
            }
        }
    }

    // ── Inactivity auto-disable ───────────────────────────────────────────────

    /**
     * Checks all tracked players for inactivity and auto-disables the perk
     * if 5 minutes have passed since last use.
     * Called by a scheduled task every 60 seconds.
     */
    public void checkInactivity() {
        long now = System.currentTimeMillis();

        Iterator<Map.Entry<UUID, Long>> iterator = lastUseTime.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            if ((now - entry.getValue()) > INACTIVITY_TIMEOUT_MS) {
                UUID playerId = entry.getKey();
                if (perkManager.isEnabled(playerId, PERK_ID)) {
                    perkManager.setEnabled(playerId, PERK_ID, false);

                    Player player = plugin.getServer().getPlayer(playerId);
                    if (player != null) {
                        player.sendMessage("§7Tree Capitator has been disabled due to inactivity.");
                    }
                }
                iterator.remove();
            }
        }
    }
}
