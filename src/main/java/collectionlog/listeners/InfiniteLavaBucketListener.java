package collectionlog.listeners;

import collectionlog.CollectionLogPlugin;
import collectionlog.perks.InfiniteLavaBucket;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.ItemStack;

public class InfiniteLavaBucketListener implements Listener {

    private final CollectionLogPlugin plugin;
    private final InfiniteLavaBucket infiniteLavaBucket;

    public InfiniteLavaBucketListener(CollectionLogPlugin plugin) {
        this.plugin = plugin;
        this.infiniteLavaBucket = plugin.getInfiniteLavaBucket();
    }

    /**
     * Intercepts right-click interactions with infinite lava bucket at the earliest priority.
     * This runs BEFORE PlayerBucketEmptyEvent is even fired.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Only handle right-click on blocks
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        // Ignore off-hand to prevent double processing
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        // Check if this is an infinite lava bucket
        if (!infiniteLavaBucket.isInfiniteLavaBucket(item)) {
            return;
        }

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }

        // Don't place lava if clicking on an interactable block (unless sneaking)
        if (!player.isSneaking() && isInteractableBlock(clickedBlock.getType())) {
            return;
        }

        BlockFace face = event.getBlockFace();
        Block targetBlock = clickedBlock.getRelative(face);

        // Check if we can place lava at this location
        if (!canPlaceLava(targetBlock)) {
            return;
        }

        // Cancel the event completely - this prevents PlayerBucketEmptyEvent from firing
        event.setCancelled(true);

        // Place the lava block
        targetBlock.setType(Material.LAVA);

        // Play sound effect
        player.playSound(targetBlock.getLocation(), Sound.ITEM_BUCKET_EMPTY_LAVA, 1.0f, 1.0f);

        // Swing arm animation for visual feedback
        player.swingMainHand();

        // Item stays as infinite lava bucket - no changes needed!
    }

    /**
     * Checks if a block is interactable (would normally consume the right-click).
     */
    private boolean isInteractableBlock(Material material) {
        return switch (material) {
            case CRAFTING_TABLE, FURNACE, BLAST_FURNACE, SMOKER,
                 CHEST, TRAPPED_CHEST, ENDER_CHEST, BARREL,
                 DISPENSER, DROPPER, HOPPER,
                 ENCHANTING_TABLE, ANVIL, CHIPPED_ANVIL, DAMAGED_ANVIL,
                 BREWING_STAND, STONECUTTER, LOOM, CARTOGRAPHY_TABLE,
                 SMITHING_TABLE, GRINDSTONE, LECTERN, COMPOSTER,
                 BELL, RESPAWN_ANCHOR, LODESTONE,
                 LEVER, STONE_BUTTON, OAK_BUTTON, SPRUCE_BUTTON,
                 BIRCH_BUTTON, JUNGLE_BUTTON, ACACIA_BUTTON, DARK_OAK_BUTTON,
                 CRIMSON_BUTTON, WARPED_BUTTON, POLISHED_BLACKSTONE_BUTTON,
                 OAK_DOOR, SPRUCE_DOOR, BIRCH_DOOR, JUNGLE_DOOR, ACACIA_DOOR,
                 DARK_OAK_DOOR, CRIMSON_DOOR, WARPED_DOOR, IRON_DOOR,
                 OAK_TRAPDOOR, SPRUCE_TRAPDOOR, BIRCH_TRAPDOOR, JUNGLE_TRAPDOOR,
                 ACACIA_TRAPDOOR, DARK_OAK_TRAPDOOR, CRIMSON_TRAPDOOR, WARPED_TRAPDOOR,
                 IRON_TRAPDOOR, OAK_FENCE_GATE, SPRUCE_FENCE_GATE, BIRCH_FENCE_GATE,
                 JUNGLE_FENCE_GATE, ACACIA_FENCE_GATE, DARK_OAK_FENCE_GATE,
                 CRIMSON_FENCE_GATE, WARPED_FENCE_GATE,
                 REPEATER, COMPARATOR, DAYLIGHT_DETECTOR,
                 NOTE_BLOCK, JUKEBOX, CAKE, DRAGON_EGG,
                 SHULKER_BOX, WHITE_SHULKER_BOX, ORANGE_SHULKER_BOX, MAGENTA_SHULKER_BOX,
                 LIGHT_BLUE_SHULKER_BOX, YELLOW_SHULKER_BOX, LIME_SHULKER_BOX,
                 PINK_SHULKER_BOX, GRAY_SHULKER_BOX, LIGHT_GRAY_SHULKER_BOX,
                 CYAN_SHULKER_BOX, PURPLE_SHULKER_BOX, BLUE_SHULKER_BOX,
                 BROWN_SHULKER_BOX, GREEN_SHULKER_BOX, RED_SHULKER_BOX, BLACK_SHULKER_BOX -> true;
            default -> false;
        };
    }

    /**
     * Checks if lava can be placed at the target location.
     */
    private boolean canPlaceLava(Block block) {
        Material type = block.getType();

        // Can replace air, water, and other liquids
        if (type == Material.AIR ||
            type == Material.CAVE_AIR ||
            type == Material.VOID_AIR ||
            type == Material.WATER ||
            type == Material.LAVA) {
            return true;
        }

        // Can replace replaceable blocks like grass, flowers, snow, etc.
        return isReplaceableBlock(type);
    }

    /**
     * Checks if a block can be replaced when placing lava.
     */
    private boolean isReplaceableBlock(Material material) {
        return switch (material) {
            case SHORT_GRASS, TALL_GRASS, FERN, LARGE_FERN,
                 DEAD_BUSH, SEAGRASS, TALL_SEAGRASS, KELP, KELP_PLANT,
                 DANDELION, POPPY, BLUE_ORCHID, ALLIUM, AZURE_BLUET,
                 RED_TULIP, ORANGE_TULIP, WHITE_TULIP, PINK_TULIP,
                 OXEYE_DAISY, CORNFLOWER, LILY_OF_THE_VALLEY, WITHER_ROSE,
                 SUNFLOWER, LILAC, ROSE_BUSH, PEONY,
                 TORCH, WALL_TORCH, SOUL_TORCH, SOUL_WALL_TORCH,
                 REDSTONE_TORCH, REDSTONE_WALL_TORCH,
                 SNOW, VINE, GLOW_LICHEN, HANGING_ROOTS,
                 FIRE, SOUL_FIRE,
                 WHEAT, CARROTS, POTATOES, BEETROOTS,
                 NETHER_WART, SWEET_BERRY_BUSH, CAVE_VINES, CAVE_VINES_PLANT,
                 SUGAR_CANE, BAMBOO, BAMBOO_SAPLING,
                 MUSHROOM_STEM, BROWN_MUSHROOM, RED_MUSHROOM,
                 CRIMSON_FUNGUS, WARPED_FUNGUS, CRIMSON_ROOTS, WARPED_ROOTS,
                 NETHER_SPROUTS, TWISTING_VINES, TWISTING_VINES_PLANT,
                 WEEPING_VINES, WEEPING_VINES_PLANT,
                 COBWEB, TRIPWIRE -> true;
            default -> false;
        };
    }

    /**
     * Prevents infinite lava bucket from being consumed when used as fuel.
     * Sets burn time but keeps the bucket in the furnace.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFurnaceBurn(FurnaceBurnEvent event) {
        ItemStack fuel = event.getFuel();

        if (!infiniteLavaBucket.isInfiniteLavaBucket(fuel)) {
            return;
        }

        // Set the burn time to match a normal lava bucket (1000 items = 20000 ticks)
        event.setBurnTime(20000);

        // Restore the infinite lava bucket after vanilla consumes it
        Block furnaceBlock = event.getBlock();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (furnaceBlock.getState() instanceof org.bukkit.block.Furnace furnace) {
                restoreInFurnaceInventory(furnace.getInventory());
            } else if (furnaceBlock.getState() instanceof org.bukkit.block.BlastFurnace blastFurnace) {
                restoreInFurnaceInventory(blastFurnace.getInventory());
            } else if (furnaceBlock.getState() instanceof org.bukkit.block.Smoker smoker) {
                restoreInFurnaceInventory(smoker.getInventory());
            }
        });
    }

    private void restoreInFurnaceInventory(FurnaceInventory inventory) {
        ItemStack currentFuel = inventory.getFuel();
        if (currentFuel == null || currentFuel.getType() == Material.BUCKET || currentFuel.getType() == Material.AIR) {
            inventory.setFuel(infiniteLavaBucket.createItem());
        }
    }
}
