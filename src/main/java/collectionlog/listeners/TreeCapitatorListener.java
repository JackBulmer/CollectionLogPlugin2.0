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

    /** Cardinal directions for direct adjacency checks. */
    private static final BlockFace[] CARDINAL_FACES = {
        BlockFace.NORTH,
        BlockFace.SOUTH,
        BlockFace.EAST,
        BlockFace.WEST,
        BlockFace.UP,
        BlockFace.DOWN
    };

    /**
     * Minimum number of structure blocks that must be directly adjacent to spruce logs
     * before the tree is classified as a structure.
     * A threshold of 3 avoids false positives from a single nearby player-placed block.
     */
    private static final int MIN_STRUCTURE_BLOCKS_FOR_SPRUCE = 3;

    /** Structure indicator blocks commonly found in villages and buildings. */
    private static final Set<Material> STRUCTURE_BLOCKS = Set.of(
            // Planks (all types)
            Material.OAK_PLANKS,
            Material.SPRUCE_PLANKS,
            Material.BIRCH_PLANKS,
            Material.JUNGLE_PLANKS,
            Material.ACACIA_PLANKS,
            Material.DARK_OAK_PLANKS,
            Material.MANGROVE_PLANKS,
            Material.CHERRY_PLANKS,
            Material.PALE_OAK_PLANKS,
            Material.CRIMSON_PLANKS,
            Material.WARPED_PLANKS,
            // Stone variants (village buildings)
            Material.COBBLESTONE,
            Material.MOSSY_COBBLESTONE,
            Material.STONE,
            Material.STONE_BRICKS,
            Material.MOSSY_STONE_BRICKS,
            Material.CRACKED_STONE_BRICKS,
            Material.CHISELED_STONE_BRICKS,
            // Fences
            Material.OAK_FENCE,
            Material.SPRUCE_FENCE,
            Material.BIRCH_FENCE,
            Material.JUNGLE_FENCE,
            Material.ACACIA_FENCE,
            Material.DARK_OAK_FENCE,
            Material.MANGROVE_FENCE,
            Material.CHERRY_FENCE,
            Material.PALE_OAK_FENCE,
            Material.CRIMSON_FENCE,
            Material.WARPED_FENCE,
            Material.NETHER_BRICK_FENCE,
            // Other common structure blocks
            Material.BRICKS,
            Material.COBWEB,
            Material.LADDER
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
     * Check if this is a natural tree using targeted detection.
     *
     * <p>SPRUCE TREES (high risk in taiga villages):
     * <ul>
     *   <li>Check for structure blocks (planks, cobblestone, fences)</li>
     *   <li>Check width vs height ratio</li>
     *   <li>Check for leaves</li>
     * </ul>
     *
     * <p>OTHER TREES (low risk):
     * <ul>
     *   <li>Check for leaves only</li>
     * </ul>
     *
     * @param startLog The bottom log of the tree
     * @param logs All logs in the tree
     * @return true if this is a natural tree, false if it's a structure
     */
    private boolean isNaturalTree(Block startLog, Set<Block> logs) {
        // Nether trees: always allow (no nether structures with stems)
        if (isNetherLog(startLog.getType())) {
            return true;
        }

        Material logType = startLog.getType();

        // SPRUCE-ONLY CHECKS (for spruce villages in taiga biomes)
        if (logType == Material.SPRUCE_LOG) {
            // Check 1: Structure blocks (need 3+ to block)
            // Requires 3+ to avoid false positives from nearby player builds
            if (hasMinimumStructureBlocks(logs, MIN_STRUCTURE_BLOCKS_FOR_SPRUCE)) {
                return false; // Spruce village house detected
            }

            // Check 2: Width vs height ratio
            // Natural trees grow upward; structures spread horizontally
            if (isWiderThanTall(logs)) {
                return false; // Horizontal structure pattern detected
            }
        }

        // For ALL trees: Check for leaves (improved radius)
        int leafCount = countNearbyLeaves(logs);
        if (leafCount >= 1) {
            return true; // Has leaves = natural tree
        }

        // No leaves found, but passed structure checks (if applicable)
        // Allow: player may have removed leaves manually
        return true;
    }

    /**
     * Counts unique leaf blocks within 3 blocks of any log.
     * Increased radius from 2 to 3 blocks to catch leaves on tall/wide trees.
     *
     * @return number of unique leaf blocks found
     */
    private int countNearbyLeaves(Set<Block> logs) {
        Set<Block> uniqueLeaves = new HashSet<>();

        for (Block log : logs) {
            for (int x = -3; x <= 3; x++) {
                for (int y = -3; y <= 3; y++) {
                    for (int z = -3; z <= 3; z++) {
                        Block adjacent = log.getRelative(x, y, z);
                        if (LEAF_TYPES.contains(adjacent.getType())) {
                            uniqueLeaves.add(adjacent);
                        }
                    }
                }
            }
        }

        return uniqueLeaves.size();
    }

    /**
     * Check if logs have at least the minimum number of unique structure blocks adjacent.
     * Uses direct contact only (6 cardinal faces) to avoid false positives.
     * Counts unique structure blocks so a single block touching multiple logs is not
     * counted more than once.
     *
     * @param logs All logs in the tree
     * @param minimum Minimum number of unique structure blocks required
     * @return true if minimum unique structure blocks found
     */
    private boolean hasMinimumStructureBlocks(Set<Block> logs, int minimum) {
        Set<Block> uniqueStructureBlocks = new HashSet<>();

        for (Block log : logs) {
            // Check only direct contact (6 cardinal directions)
            for (BlockFace face : CARDINAL_FACES) {
                Block adjacent = log.getRelative(face);
                if (STRUCTURE_BLOCKS.contains(adjacent.getType())) {
                    uniqueStructureBlocks.add(adjacent);

                    // Early exit if we've found enough
                    if (uniqueStructureBlocks.size() >= minimum) {
                        return true;
                    }
                }
            }
        }

        return false; // Less than minimum unique structure blocks found
    }

    /**
     * Check if tree is wider than it is tall.
     * Natural trees grow upward; structures spread horizontally.
     *
     * @param logs All logs in the tree
     * @return true if width > height (structure pattern)
     */
    private boolean isWiderThanTall(Set<Block> logs) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;

        for (Block log : logs) {
            int x = log.getX(), y = log.getY(), z = log.getZ();
            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
            if (z < minZ) minZ = z;
            if (z > maxZ) maxZ = z;
        }

        int height = maxY - minY + 1;
        int width = Math.max(maxX - minX + 1, maxZ - minZ + 1);

        // If width > height, it's wider than tall (structure pattern)
        // Equal dimensions are allowed to handle narrow/square natural trees
        return width > height;
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
