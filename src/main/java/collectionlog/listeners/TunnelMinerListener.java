package collectionlog.listeners;

import collectionlog.CollectionLogPlugin;
import collectionlog.database.dao.BlockOwnershipDAO;
import collectionlog.perks.PerkManager;
import collectionlog.utils.GriefPreventionUtil;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Listener for the Tunnel Miner perk.
 * Automatically breaks a 3×3 area of blocks perpendicular to the player's facing direction
 * when a block is broken with a pickaxe or shovel.
 */
public class TunnelMinerListener implements Listener {

    private static final String PERK_ID = "tunnel_miner";

    private final CollectionLogPlugin plugin;
    private final PerkManager perkManager;
    private final BlockOwnershipDAO blockOwnershipDAO;
    private final InstantGlassSmeltingListener instantGlassListener;

    /** Guards against re-entrant BlockBreakEvent calls triggered during tunnel mining. */
    private final Set<UUID> activeTunnelMiners = new HashSet<>();

    public TunnelMinerListener(CollectionLogPlugin plugin) {
        this.plugin = plugin;
        this.perkManager = plugin.getPerkManager();
        this.blockOwnershipDAO = plugin.getBlockOwnershipDAO();
        this.instantGlassListener = plugin.getInstantGlassSmeltingListener();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        // Creative mode players don't get perk benefits
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        // Prevent re-entrant calls triggered by breakBlockWithTool
        UUID playerId = player.getUniqueId();
        if (activeTunnelMiners.contains(playerId)) {
            return;
        }

        // Check if player has perk unlocked and enabled
        if (!perkManager.hasUnlocked(playerId, PERK_ID) || !perkManager.isEnabled(playerId, PERK_ID)) {
            return;
        }

        // Must use a pickaxe or shovel
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!isPickaxe(tool.getType()) && !isShovel(tool.getType())) {
            return;
        }

        // Check if block is player-placed (skip if owned)
        if (blockOwnershipDAO.getOwner(block.getLocation()) != null) {
            return;
        }

        // Check GriefPrevention permissions
        if (!GriefPreventionUtil.canBreak(player, block.getLocation(), block.getType())) {
            return;
        }

        // Mine the 3×3 tunnel (origin block already broken by vanilla via the event)
        activeTunnelMiners.add(playerId);
        try {
            mineTunnel(player, block, tool);
        } finally {
            activeTunnelMiners.remove(playerId);
        }
    }

    // ── Tunnel mining ─────────────────────────────────────────────────────────

    /**
     * Mines the 8 surrounding blocks in the 3×3 plane perpendicular to the player's
     * facing direction. The origin block is handled by vanilla.
     */
    private void mineTunnel(Player player, Block originBlock, ItemStack tool) {
        TunnelDirection tunnelDir = getTunnelDirection(player);
        List<Block> tunnelBlocks = get3x3Blocks(originBlock, tunnelDir);

        boolean usingShovel = isShovel(tool.getType());

        for (Block block : tunnelBlocks) {
            if (isToolBroken(tool)) {
                break;
            }

            // Skip player-placed blocks
            if (blockOwnershipDAO.getOwner(block.getLocation()) != null) {
                continue;
            }

            // Skip blocks the player doesn't have permission to break
            if (!GriefPreventionUtil.canBreak(player, block.getLocation(), block.getType())) {
                continue;
            }

            // Skip blocks the tool cannot break
            if (!canBreakBlock(tool.getType(), block.getType())) {
                continue;
            }

            // Determine if this sand block should become glass (instant glass integration)
            boolean convertToGlass = usingShovel
                    && instantGlassListener != null
                    && instantGlassListener.shouldConvertToGlass(player, block);

            breakBlockWithTool(player, block, tool, convertToGlass);
        }
    }

    /**
     * Breaks a single block, applying all relevant enchantment/instant-glass logic,
     * dropping items and XP, and consuming tool durability.
     */
    private void breakBlockWithTool(Player player, Block block, ItemStack tool, boolean convertToGlass) {
        BlockBreakEvent breakEvent = new BlockBreakEvent(block, player);
        plugin.getServer().getPluginManager().callEvent(breakEvent);

        if (breakEvent.isCancelled()) {
            return;
        }

        Location dropLoc = block.getLocation().add(0.5, 0.5, 0.5);

        if (convertToGlass) {
            // Drop glass and play instant-glass effects
            block.getWorld().dropItemNaturally(dropLoc, new ItemStack(Material.GLASS, 1));

            block.getWorld().spawnParticle(Particle.FLAME, dropLoc, 50, 0.4, 0.4, 0.4, 0.03);
            block.getWorld().spawnParticle(Particle.SMOKE, dropLoc, 20, 0.3, 0.3, 0.3, 0.05);
            block.getWorld().spawnParticle(Particle.LAVA, dropLoc, 10, 0.3, 0.3, 0.3, 0.0);
            block.getWorld().playSound(dropLoc, Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 1.5f);
            // No XP for instant glass
        } else {
            // Normal drops respecting Fortune / Silk Touch
            Collection<ItemStack> drops = block.getDrops(tool, player);
            for (ItemStack drop : drops) {
                block.getWorld().dropItemNaturally(dropLoc, drop);
            }

            // XP only when not using Silk Touch (vanilla behaviour)
            if (!tool.containsEnchantment(Enchantment.SILK_TOUCH)) {
                int xp = getBlockExperience(block.getType());
                if (xp > 0) {
                    block.getWorld().spawn(dropLoc, org.bukkit.entity.ExperienceOrb.class)
                            .setExperience(xp);
                }
            }
        }

        block.setType(Material.AIR);
        damageTool(tool, player);
    }

    // ── Direction helpers ─────────────────────────────────────────────────────

    private enum TunnelDirection {
        NORTH, SOUTH, EAST, WEST, UP, DOWN
    }

    /**
     * Determines the tunnel direction from the player's pitch and yaw.
     * <p>
     * Bukkit pitch: −90 = looking straight up, 0 = horizontal, +90 = looking straight down.
     * Bukkit yaw: 0/360 = South, 90 = West, 180 = North, 270 = East.
     */
    private TunnelDirection getTunnelDirection(Player player) {
        float pitch = player.getLocation().getPitch();

        if (pitch < -45.0f) {
            return TunnelDirection.UP;
        } else if (pitch > 45.0f) {
            return TunnelDirection.DOWN;
        }

        // Normalise yaw to [0, 360)
        float yaw = player.getLocation().getYaw() % 360.0f;
        if (yaw < 0) yaw += 360.0f;

        if (yaw >= 315.0f || yaw < 45.0f) {
            return TunnelDirection.SOUTH;
        } else if (yaw < 135.0f) {
            return TunnelDirection.WEST;
        } else if (yaw < 225.0f) {
            return TunnelDirection.NORTH;
        } else {
            return TunnelDirection.EAST;
        }
    }

    /**
     * Returns the 8 blocks surrounding the origin in the 3×3 plane perpendicular
     * to the given tunnel direction (the origin block itself is excluded).
     */
    private List<Block> get3x3Blocks(Block origin, TunnelDirection direction) {
        List<Block> blocks = new ArrayList<>(8);

        switch (direction) {
            case NORTH, SOUTH -> {
                // XY plane (Z fixed)
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        if (dx == 0 && dy == 0) continue;
                        blocks.add(origin.getRelative(dx, dy, 0));
                    }
                }
            }
            case EAST, WEST -> {
                // ZY plane (X fixed)
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        if (dz == 0 && dy == 0) continue;
                        blocks.add(origin.getRelative(0, dy, dz));
                    }
                }
            }
            case UP, DOWN -> {
                // XZ plane (Y fixed)
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) continue;
                        blocks.add(origin.getRelative(dx, 0, dz));
                    }
                }
            }
        }

        return blocks;
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
     * Returns the vanilla XP amount for breaking the given block.
     */
    private int getBlockExperience(Material material) {
        return switch (material) {
            case COAL_ORE, DEEPSLATE_COAL_ORE -> randomRange(0, 2);
            case REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE -> randomRange(1, 5);
            case LAPIS_ORE, DEEPSLATE_LAPIS_ORE -> randomRange(2, 5);
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE -> randomRange(3, 7);
            case EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> randomRange(3, 7);
            case NETHER_QUARTZ_ORE -> randomRange(2, 5);
            case NETHER_GOLD_ORE -> randomRange(0, 1);
            default -> 0;
        };
    }

    private int randomRange(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    // ── Block / tool helpers ──────────────────────────────────────────────────

    private boolean isPickaxe(Material material) {
        return switch (material) {
            case WOODEN_PICKAXE, STONE_PICKAXE, IRON_PICKAXE,
                 GOLDEN_PICKAXE, DIAMOND_PICKAXE, NETHERITE_PICKAXE -> true;
            default -> false;
        };
    }

    private boolean isShovel(Material material) {
        return switch (material) {
            case WOODEN_SHOVEL, STONE_SHOVEL, IRON_SHOVEL,
                 GOLDEN_SHOVEL, DIAMOND_SHOVEL, NETHERITE_SHOVEL -> true;
            default -> false;
        };
    }

    /**
     * Returns {@code true} if the given tool can break the given block type,
     * respecting vanilla tool-material requirements.
     */
    private boolean canBreakBlock(Material tool, Material block) {
        if (isShovel(tool)) {
            return isShovelEffective(block);
        }
        if (isPickaxe(tool)) {
            return isPickaxeEffective(tool, block);
        }
        return false;
    }

    private boolean isShovelEffective(Material block) {
        return switch (block) {
            case DIRT, GRASS_BLOCK, COARSE_DIRT, PODZOL, MYCELIUM,
                 ROOTED_DIRT, MUD, MUDDY_MANGROVE_ROOTS,
                 DIRT_PATH, FARMLAND,
                 SAND, RED_SAND, GRAVEL, CLAY, SNOW, SNOW_BLOCK,
                 POWDER_SNOW, SOUL_SAND, SOUL_SOIL -> true;
            default -> false;
        };
    }

    private boolean isPickaxeEffective(Material pickaxe, Material block) {
        if (!isPickaxeMineable(block)) {
            return false;
        }
        return getPickaxeTier(pickaxe) >= getRequiredMiningTier(block);
    }

    private boolean isPickaxeMineable(Material block) {
        return switch (block) {
            // Stone / cobblestone variants
            case STONE, COBBLESTONE, SMOOTH_STONE,
                 STONE_BRICKS, CRACKED_STONE_BRICKS, MOSSY_STONE_BRICKS, CHISELED_STONE_BRICKS,
                 STONE_SLAB, COBBLESTONE_SLAB, SMOOTH_STONE_SLAB,
                 STONE_STAIRS, COBBLESTONE_STAIRS,
                 STONE_BRICK_SLAB, STONE_BRICK_STAIRS,
            // Deepslate variants
                 DEEPSLATE, COBBLED_DEEPSLATE, POLISHED_DEEPSLATE,
                 DEEPSLATE_BRICKS, CRACKED_DEEPSLATE_BRICKS, CHISELED_DEEPSLATE,
                 DEEPSLATE_TILES, CRACKED_DEEPSLATE_TILES,
                 COBBLED_DEEPSLATE_SLAB, COBBLED_DEEPSLATE_STAIRS,
                 POLISHED_DEEPSLATE_SLAB, POLISHED_DEEPSLATE_STAIRS,
                 DEEPSLATE_BRICK_SLAB, DEEPSLATE_BRICK_STAIRS,
                 DEEPSLATE_TILE_SLAB, DEEPSLATE_TILE_STAIRS,
            // Andesite / Diorite / Granite variants
                 ANDESITE, POLISHED_ANDESITE, ANDESITE_SLAB, ANDESITE_STAIRS,
                 DIORITE, POLISHED_DIORITE, DIORITE_SLAB, DIORITE_STAIRS,
                 GRANITE, POLISHED_GRANITE, GRANITE_SLAB, GRANITE_STAIRS,
            // Basalt / Blackstone / Netherrack
                 BASALT, POLISHED_BASALT, SMOOTH_BASALT,
                 BLACKSTONE, POLISHED_BLACKSTONE,
                 POLISHED_BLACKSTONE_BRICKS, CRACKED_POLISHED_BLACKSTONE_BRICKS,
                 CHISELED_POLISHED_BLACKSTONE,
                 BLACKSTONE_SLAB, BLACKSTONE_STAIRS,
                 POLISHED_BLACKSTONE_SLAB, POLISHED_BLACKSTONE_STAIRS,
                 POLISHED_BLACKSTONE_BRICK_SLAB, POLISHED_BLACKSTONE_BRICK_STAIRS,
                 NETHERRACK,
            // Tuff / Calcite / Amethyst
                 TUFF, CALCITE, AMETHYST_BLOCK, BUDDING_AMETHYST,
            // Sandstone variants
                 SANDSTONE, CHISELED_SANDSTONE, CUT_SANDSTONE, SMOOTH_SANDSTONE,
                 SANDSTONE_SLAB, CUT_SANDSTONE_SLAB, SMOOTH_SANDSTONE_SLAB,
                 SANDSTONE_STAIRS, SMOOTH_SANDSTONE_STAIRS,
                 RED_SANDSTONE, CHISELED_RED_SANDSTONE, CUT_RED_SANDSTONE, SMOOTH_RED_SANDSTONE,
                 RED_SANDSTONE_SLAB, CUT_RED_SANDSTONE_SLAB, SMOOTH_RED_SANDSTONE_SLAB,
                 RED_SANDSTONE_STAIRS, SMOOTH_RED_SANDSTONE_STAIRS,
            // End stone
                 END_STONE, END_STONE_BRICKS, END_STONE_BRICK_SLAB, END_STONE_BRICK_STAIRS,
            // Prismarine
                 PRISMARINE, PRISMARINE_BRICKS, DARK_PRISMARINE,
                 PRISMARINE_SLAB, PRISMARINE_BRICK_SLAB, DARK_PRISMARINE_SLAB,
                 PRISMARINE_STAIRS, PRISMARINE_BRICK_STAIRS, DARK_PRISMARINE_STAIRS,
                 SEA_LANTERN,
            // Terracotta
                 TERRACOTTA,
                 WHITE_TERRACOTTA, ORANGE_TERRACOTTA, MAGENTA_TERRACOTTA, LIGHT_BLUE_TERRACOTTA,
                 YELLOW_TERRACOTTA, LIME_TERRACOTTA, PINK_TERRACOTTA, GRAY_TERRACOTTA,
                 LIGHT_GRAY_TERRACOTTA, CYAN_TERRACOTTA, PURPLE_TERRACOTTA, BLUE_TERRACOTTA,
                 BROWN_TERRACOTTA, GREEN_TERRACOTTA, RED_TERRACOTTA, BLACK_TERRACOTTA,
                 WHITE_GLAZED_TERRACOTTA, ORANGE_GLAZED_TERRACOTTA, MAGENTA_GLAZED_TERRACOTTA,
                 LIGHT_BLUE_GLAZED_TERRACOTTA, YELLOW_GLAZED_TERRACOTTA, LIME_GLAZED_TERRACOTTA,
                 PINK_GLAZED_TERRACOTTA, GRAY_GLAZED_TERRACOTTA, LIGHT_GRAY_GLAZED_TERRACOTTA,
                 CYAN_GLAZED_TERRACOTTA, PURPLE_GLAZED_TERRACOTTA, BLUE_GLAZED_TERRACOTTA,
                 BROWN_GLAZED_TERRACOTTA, GREEN_GLAZED_TERRACOTTA, RED_GLAZED_TERRACOTTA,
                 BLACK_GLAZED_TERRACOTTA,
            // Concrete (hardened)
                 WHITE_CONCRETE, ORANGE_CONCRETE, MAGENTA_CONCRETE, LIGHT_BLUE_CONCRETE,
                 YELLOW_CONCRETE, LIME_CONCRETE, PINK_CONCRETE, GRAY_CONCRETE,
                 LIGHT_GRAY_CONCRETE, CYAN_CONCRETE, PURPLE_CONCRETE, BLUE_CONCRETE,
                 BROWN_CONCRETE, GREEN_CONCRETE, RED_CONCRETE, BLACK_CONCRETE,
            // Ores (overworld)
                 COAL_ORE, IRON_ORE, GOLD_ORE, DIAMOND_ORE, EMERALD_ORE,
                 LAPIS_ORE, REDSTONE_ORE, COPPER_ORE,
                 DEEPSLATE_COAL_ORE, DEEPSLATE_IRON_ORE, DEEPSLATE_GOLD_ORE,
                 DEEPSLATE_DIAMOND_ORE, DEEPSLATE_EMERALD_ORE, DEEPSLATE_LAPIS_ORE,
                 DEEPSLATE_REDSTONE_ORE, DEEPSLATE_COPPER_ORE,
            // Ores (nether)
                 NETHER_GOLD_ORE, NETHER_QUARTZ_ORE,
            // Raw ore blocks
                 RAW_IRON_BLOCK, RAW_GOLD_BLOCK, RAW_COPPER_BLOCK,
            // Special
                 ANCIENT_DEBRIS, OBSIDIAN, CRYING_OBSIDIAN,
                 NETHER_BRICKS, CRACKED_NETHER_BRICKS, CHISELED_NETHER_BRICKS,
                 RED_NETHER_BRICKS,
                 NETHER_BRICK_SLAB, RED_NETHER_BRICK_SLAB,
                 NETHER_BRICK_STAIRS, RED_NETHER_BRICK_STAIRS,
                 IRON_BLOCK, GOLD_BLOCK, DIAMOND_BLOCK, EMERALD_BLOCK,
                 LAPIS_BLOCK, REDSTONE_BLOCK, COPPER_BLOCK,
                 EXPOSED_COPPER, WEATHERED_COPPER, OXIDIZED_COPPER,
                 CUT_COPPER, EXPOSED_CUT_COPPER, WEATHERED_CUT_COPPER, OXIDIZED_CUT_COPPER,
                 COAL_BLOCK, NETHERITE_BLOCK,
                 DISPENSER, DROPPER, OBSERVER, PISTON, STICKY_PISTON,
                 FURNACE, BLAST_FURNACE, SMOKER -> true;
            default -> false;
        };
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

    private int getRequiredMiningTier(Material block) {
        return switch (block) {
            // Tier 0: any pickaxe
            case COAL_ORE, DEEPSLATE_COAL_ORE, COPPER_ORE, DEEPSLATE_COPPER_ORE,
                 NETHER_GOLD_ORE, NETHER_QUARTZ_ORE,
                 STONE, COBBLESTONE, SMOOTH_STONE,
                 NETHERRACK, SANDSTONE, RED_SANDSTONE,
                 ANDESITE, DIORITE, GRANITE, BLACKSTONE,
                 DEEPSLATE, COBBLED_DEEPSLATE, END_STONE,
                 BASALT, TUFF, CALCITE -> 0;

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
            case OBSIDIAN, CRYING_OBSIDIAN, ANCIENT_DEBRIS -> 3;

            default -> 0;
        };
    }
}
