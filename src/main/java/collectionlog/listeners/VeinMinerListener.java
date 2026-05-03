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
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Listener for the Vein Miner perk.
 * Automatically mines entire ore veins when breaking a single ore block with a pickaxe.
 */
public class VeinMinerListener implements Listener {

    private static final String PERK_ID = "vein_miner";
    private static final int MAX_VEIN_SIZE = 64;

    private final CollectionLogPlugin plugin;
    private final PerkManager perkManager;
    private final BlockOwnershipDAO blockOwnershipDAO;

    /** Guards against re-entrant BlockBreakEvent calls triggered during vein mining. */
    private final Set<UUID> activeVeinMiners = new HashSet<>();

    // Offsets for all 26 adjacent blocks (including diagonals)
    private static final int[][] ADJACENT_OFFSETS = {
        // Same Y level
        {-1, 0, 0}, {1, 0, 0}, {0, 0, -1}, {0, 0, 1},
        {-1, 0, -1}, {-1, 0, 1}, {1, 0, -1}, {1, 0, 1},
        // Above
        {-1, 1, 0}, {1, 1, 0}, {0, 1, -1}, {0, 1, 1},
        {-1, 1, -1}, {-1, 1, 1}, {1, 1, -1}, {1, 1, 1},
        {0, 1, 0},
        // Below
        {-1, -1, 0}, {1, -1, 0}, {0, -1, -1}, {0, -1, 1},
        {-1, -1, -1}, {-1, -1, 1}, {1, -1, -1}, {1, -1, 1},
        {0, -1, 0}
    };

    public VeinMinerListener(CollectionLogPlugin plugin) {
        this.plugin = plugin;
        this.perkManager = plugin.getPerkManager();
        this.blockOwnershipDAO = plugin.getBlockOwnershipDAO();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material blockType = block.getType();

        // Only process ore blocks
        if (!isOreBlock(blockType)) {
            return;
        }

        // Creative mode players don't get perk benefits
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        // Prevent re-entrant calls triggered by breakBlockWithTool
        UUID playerId = player.getUniqueId();
        if (activeVeinMiners.contains(playerId)) {
            return;
        }

        // Check if player has perk unlocked and enabled
        if (!perkManager.hasUnlocked(playerId, PERK_ID) || !perkManager.isEnabled(playerId, PERK_ID)) {
            return;
        }

        // Must use a pickaxe
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!isPickaxe(tool.getType())) {
            return;
        }

        // Check if tool can mine this ore type; if not, let vanilla handle it normally
        if (!canMineOre(tool.getType(), blockType)) {
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

        // Find and mine the vein (origin block is handled by vanilla via the event)
        activeVeinMiners.add(playerId);
        try {
            mineVein(player, block, tool);
        } finally {
            activeVeinMiners.remove(playerId);
        }
    }

    // ── Vein mining ───────────────────────────────────────────────────────────

    /**
     * Finds all ore blocks in the connected vein via BFS and mines them.
     * The origin block is already being broken by vanilla (via the event),
     * so only the additional connected blocks are broken here.
     */
    private void mineVein(Player player, Block originBlock, ItemStack tool) {
        Material oreType = originBlock.getType();
        Set<Location> visited = new HashSet<>();
        Queue<Block> queue = new LinkedList<>();
        List<Block> veinBlocks = new ArrayList<>();

        queue.add(originBlock);
        visited.add(originBlock.getLocation());

        while (!queue.isEmpty() && veinBlocks.size() < MAX_VEIN_SIZE) {
            Block current = queue.poll();
            veinBlocks.add(current);

            for (int[] offset : ADJACENT_OFFSETS) {
                Block adjacent = current.getRelative(offset[0], offset[1], offset[2]);
                Location adjacentLoc = adjacent.getLocation();

                if (visited.contains(adjacentLoc)) {
                    continue;
                }
                visited.add(adjacentLoc);

                if (adjacent.getType() != oreType) {
                    continue;
                }

                if (blockOwnershipDAO.getOwner(adjacentLoc) != null) {
                    continue;
                }

                if (!GriefPreventionUtil.canBreak(player, adjacentLoc, oreType)) {
                    continue;
                }

                queue.add(adjacent);
            }
        }

        // Skip index 0 – the origin block is already broken by vanilla
        for (int i = 1; i < veinBlocks.size(); i++) {
            if (isToolBroken(tool)) {
                break;
            }
            breakBlockWithTool(player, veinBlocks.get(i), tool);
        }
    }

    /**
     * Breaks a single block, dropping items and XP as vanilla would,
     * while respecting Fortune and Silk Touch enchantments.
     */
    private void breakBlockWithTool(Player player, Block block, ItemStack tool) {
        BlockBreakEvent breakEvent = new BlockBreakEvent(block, player);
        plugin.getServer().getPluginManager().callEvent(breakEvent);

        if (breakEvent.isCancelled()) {
            return;
        }

        Location dropLoc = block.getLocation().add(0.5, 0.5, 0.5);

        // Get drops respecting Fortune / Silk Touch
        Collection<ItemStack> drops = block.getDrops(tool, player);
        for (ItemStack drop : drops) {
            block.getWorld().dropItemNaturally(dropLoc, drop);
        }

        // Drop XP only when not using Silk Touch (vanilla behaviour)
        if (!tool.containsEnchantment(Enchantment.SILK_TOUCH)) {
            int xp = getOreExperience(block.getType());
            if (xp > 0) {
                block.getWorld().spawn(dropLoc, org.bukkit.entity.ExperienceOrb.class)
                        .setExperience(xp);
            }
        }

        block.setType(Material.AIR);

        damageTool(tool, player);
    }

    // ── Tool durability ───────────────────────────────────────────────────────

    /**
     * Damages the tool by 1, respecting the Unbreaking enchantment.
     * Breaks the tool (removes from hand) if durability reaches maximum damage.
     */
    private void damageTool(ItemStack tool, Player player) {
        if (tool == null || tool.getType() == Material.AIR) {
            return;
        }

        ItemMeta meta = tool.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return;
        }

        // Unbreaking: chance of (1 / (level + 1)) to actually consume durability
        int unbreakingLevel = tool.getEnchantmentLevel(Enchantment.UNBREAKING);
        if (unbreakingLevel > 0) {
            double chance = 1.0 / (unbreakingLevel + 1);
            if (ThreadLocalRandom.current().nextDouble() > chance) {
                return; // Durability preserved this time
            }
        }

        int newDamage = damageable.getDamage() + 1;
        if (newDamage >= tool.getType().getMaxDurability()) {
            player.getInventory().setItemInMainHand(null);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
        } else {
            damageable.setDamage(newDamage);
            tool.setItemMeta(meta);
        }
    }

    /**
     * Returns {@code true} if the tool has no remaining durability.
     */
    private boolean isToolBroken(ItemStack tool) {
        if (tool == null || tool.getType() == Material.AIR) {
            return true;
        }
        ItemMeta meta = tool.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return false;
        }
        return damageable.getDamage() >= tool.getType().getMaxDurability();
    }

    // ── XP ────────────────────────────────────────────────────────────────────

    /**
     * Returns the vanilla XP amount for breaking the given ore block.
     */
    private int getOreExperience(Material ore) {
        return switch (ore) {
            case COAL_ORE, DEEPSLATE_COAL_ORE -> randomRange(0, 2);
            case REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE -> randomRange(1, 5);
            case LAPIS_ORE, DEEPSLATE_LAPIS_ORE -> randomRange(2, 5);
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE -> randomRange(3, 7);
            case EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> randomRange(3, 7);
            case NETHER_QUARTZ_ORE -> randomRange(2, 5);
            case NETHER_GOLD_ORE -> randomRange(0, 1);
            // Copper, iron, gold, ancient debris, and raw ore blocks drop 0 XP
            default -> 0;
        };
    }

    private int randomRange(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    // ── Block / tool helpers ──────────────────────────────────────────────────

    /**
     * Returns {@code true} if the material is an ore block eligible for vein mining.
     */
    private boolean isOreBlock(Material material) {
        if (material.name().endsWith("_ORE")) {
            return true;
        }
        return material == Material.ANCIENT_DEBRIS
                || material == Material.RAW_IRON_BLOCK
                || material == Material.RAW_GOLD_BLOCK
                || material == Material.RAW_COPPER_BLOCK;
    }

    private boolean isPickaxe(Material material) {
        return switch (material) {
            case WOODEN_PICKAXE, STONE_PICKAXE, IRON_PICKAXE,
                 GOLDEN_PICKAXE, DIAMOND_PICKAXE, NETHERITE_PICKAXE -> true;
            default -> false;
        };
    }

    /**
     * Returns {@code true} if the pickaxe tier is sufficient to mine the ore.
     */
    private boolean canMineOre(Material pickaxe, Material ore) {
        return getPickaxeTier(pickaxe) >= getRequiredMiningTier(ore);
    }

    private int getPickaxeTier(Material pickaxe) {
        return switch (pickaxe) {
            case WOODEN_PICKAXE, GOLDEN_PICKAXE -> 0;
            case STONE_PICKAXE -> 1;
            case IRON_PICKAXE -> 2;
            case DIAMOND_PICKAXE -> 3;
            case NETHERITE_PICKAXE -> 4;
            default -> 0;
        };
    }

    private int getRequiredMiningTier(Material ore) {
        return switch (ore) {
            // Tier 0: any pickaxe
            case COAL_ORE, DEEPSLATE_COAL_ORE,
                 COPPER_ORE, DEEPSLATE_COPPER_ORE,
                 NETHER_GOLD_ORE, NETHER_QUARTZ_ORE -> 0;

            // Tier 1: stone pickaxe or better
            case IRON_ORE, DEEPSLATE_IRON_ORE,
                 LAPIS_ORE, DEEPSLATE_LAPIS_ORE,
                 RAW_IRON_BLOCK, RAW_COPPER_BLOCK -> 1;

            // Tier 2: iron pickaxe or better
            case GOLD_ORE, DEEPSLATE_GOLD_ORE,
                 REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE,
                 DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE,
                 EMERALD_ORE, DEEPSLATE_EMERALD_ORE,
                 RAW_GOLD_BLOCK -> 2;

            // Tier 3: diamond pickaxe or better
            case ANCIENT_DEBRIS -> 3;

            default -> 0;
        };
    }
}
