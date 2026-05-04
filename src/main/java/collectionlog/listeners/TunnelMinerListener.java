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

            // Let Minecraft handle whether the tool can break the block via block.getDrops()
            // The breakBlockWithTool method will check if drops exist and skip if empty

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
            // Let Minecraft handle everything: drops, enchantments, tool validation
            Collection<ItemStack> drops = block.getDrops(tool, player);

            // If drops are empty, the tool can't break this block properly - skip it
            if (drops.isEmpty()) {
                return; // Tool insufficient or block unbreakable, don't break it
            }

            for (ItemStack drop : drops) {
                block.getWorld().dropItemNaturally(dropLoc, drop);
            }

            // XP only when not using Silk Touch (vanilla behaviour)
            if (!tool.containsEnchantment(Enchantment.SILK_TOUCH)) {
                int xp = block.getExpDrop();
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

}
