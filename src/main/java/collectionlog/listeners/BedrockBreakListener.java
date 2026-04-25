package collectionlog.listeners;

import collectionlog.CollectionLogPlugin;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
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
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Allows breaking bedrock with a Netherite Efficiency 5 pickaxe.
 * Takes 20 seconds to break and drops bedrock as an item.
 * Shows progress via action bar.
 */
public class BedrockBreakListener implements Listener {

    private final CollectionLogPlugin plugin;
    private final Map<UUID, BedrockMiningSession> activeSessions = new HashMap<>();

    private static final int MINING_TIME_SECONDS = 20;

    public BedrockBreakListener(CollectionLogPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.BEDROCK) {
            return;
        }

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (player.getGameMode() != GameMode.SURVIVAL) {
            return;
        }

        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool == null || tool.getType() != Material.NETHERITE_PICKAXE) {
            return;
        }

        int efficiencyLevel = tool.getEnchantmentLevel(Enchantment.EFFICIENCY);
        if (efficiencyLevel < 5) {
            return;
        }

        // Check if already mining this block
        BedrockMiningSession existingSession = activeSessions.get(playerId);
        if (existingSession != null && existingSession.block.equals(block.getLocation())) {
            return;
        }

        // Cancel any existing session
        if (existingSession != null) {
            existingSession.cancel();
            activeSessions.remove(playerId);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
        }

        // Start new mining session
        startMining(player, block, tool);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // Cancel mining if player moves position (getTo() can be null for look-only events)
        if (event.getTo() == null ||
                event.getFrom().getBlockX() != event.getTo().getBlockX() ||
                event.getFrom().getBlockY() != event.getTo().getBlockY() ||
                event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {

            UUID playerId = event.getPlayer().getUniqueId();
            BedrockMiningSession session = activeSessions.get(playerId);
            if (session != null) {
                session.cancel();
                activeSessions.remove(playerId);
                event.getPlayer().spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
            }
        }
    }

    private void startMining(Player player, Block block, ItemStack tool) {
        UUID playerId = player.getUniqueId();
        Location blockLoc = block.getLocation();

        BedrockMiningSession session = new BedrockMiningSession(blockLoc);
        activeSessions.put(playerId, session);

        BukkitRunnable runnable = new BukkitRunnable() {
            int secondsElapsed = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    activeSessions.remove(playerId);
                    cancel();
                    return;
                }

                if (block.getType() != Material.BEDROCK) {
                    activeSessions.remove(playerId);
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
                    cancel();
                    return;
                }

                ItemStack currentTool = player.getInventory().getItemInMainHand();
                if (currentTool == null || currentTool.getType() != Material.NETHERITE_PICKAXE) {
                    activeSessions.remove(playerId);
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
                    cancel();
                    return;
                }

                secondsElapsed++;

                int progressBars = secondsElapsed;
                int remainingBars = MINING_TIME_SECONDS - secondsElapsed;

                StringBuilder progressDisplay = new StringBuilder();
                progressDisplay.append("§6Mining Bedrock: §a");
                for (int i = 0; i < progressBars; i++) {
                    progressDisplay.append("|");
                }
                progressDisplay.append("§7");
                for (int i = 0; i < remainingBars; i++) {
                    progressDisplay.append("|");
                }
                progressDisplay.append(" §e").append(secondsElapsed).append("/").append(MINING_TIME_SECONDS).append("s");

                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(progressDisplay.toString()));

                // Play mining sound
                player.playSound(blockLoc, Sound.BLOCK_STONE_HIT, 0.5f, 0.8f);

                if (secondsElapsed >= MINING_TIME_SECONDS) {
                    // Break the bedrock
                    block.setType(Material.AIR);
                    block.getWorld().dropItemNaturally(blockLoc, new ItemStack(Material.BEDROCK, 1));

                    player.playSound(blockLoc, Sound.BLOCK_STONE_BREAK, 1.0f, 0.8f);
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§a✔ Bedrock broken!"));

                    plugin.getLogger().info("⛏️ Player " + player.getName() + " broke bedrock");

                    // Damage the tool
                    damageTool(currentTool, player);

                    activeSessions.remove(playerId);
                    cancel();
                }
            }
        };

        session.task = runnable.runTaskTimer(plugin, 0L, 20L);
    }

    private void damageTool(ItemStack tool, Player player) {
        if (tool.getType().getMaxDurability() > 0) {
            int unbreakingLevel = tool.getEnchantmentLevel(Enchantment.UNBREAKING);

            if (unbreakingLevel > 0) {
                double chance = 1.0 / (unbreakingLevel + 1);
                if (Math.random() > chance) {
                    return;
                }
            }

            org.bukkit.inventory.meta.Damageable meta = (org.bukkit.inventory.meta.Damageable) tool.getItemMeta();
            if (meta != null) {
                int currentDamage = meta.getDamage();
                int newDamage = currentDamage + 1;

                if (newDamage >= tool.getType().getMaxDurability()) {
                    player.getInventory().setItemInMainHand(null);
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                } else {
                    meta.setDamage(newDamage);
                    tool.setItemMeta(meta);
                }
            }
        }
    }

    private static class BedrockMiningSession {
        Location block;
        BukkitTask task;

        BedrockMiningSession(Location block) {
            this.block = block;
        }

        void cancel() {
            if (task != null) {
                task.cancel();
            }
        }
    }
}
