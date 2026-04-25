package collectionlog.managers;

import collectionlog.CollectionLogPlugin;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages strafe cooldowns and fall damage protection.
 */
public class StrafeManager {
    private final CollectionLogPlugin plugin;
    private final Map<UUID, Long> strafeCooldowns = new HashMap<>();
    private final Map<UUID, BukkitTask> activeRechargeDisplays = new HashMap<>();
    private final Map<UUID, Integer> rechargeProgress = new HashMap<>();
    private final Map<UUID, Long> fallDamageProtection = new HashMap<>();

    private static final int COOLDOWN_SECONDS = 5;
    private static final String PROGRESS_BAR = "||||||||||||||||||||";
    private static final long FALL_DAMAGE_PROTECTION_MS = 3000; // 3 seconds

    public StrafeManager(CollectionLogPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean canStrafe(Player player) {
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        Long lastUsed = strafeCooldowns.get(playerId);

        if (lastUsed == null) {
            return true;
        }

        return (currentTime - lastUsed) >= (COOLDOWN_SECONDS * 1000L);
    }

    public void useStrafe(Player player) {
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        strafeCooldowns.put(playerId, currentTime);

        // Grant fall damage protection
        fallDamageProtection.put(playerId, currentTime + FALL_DAMAGE_PROTECTION_MS);

        // Start recharge display
        startRechargeDisplay(player);
    }

    private void startRechargeDisplay(Player player) {
        UUID playerId = player.getUniqueId();

        BukkitTask existingTask = activeRechargeDisplays.get(playerId);
        if (existingTask != null) {
            existingTask.cancel();
        }

        rechargeProgress.put(playerId, 0);

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    activeRechargeDisplays.remove(playerId);
                    rechargeProgress.remove(playerId);
                    return;
                }

                int progress = rechargeProgress.getOrDefault(playerId, 0);
                int totalTicks = COOLDOWN_SECONDS * 20;

                if (progress >= totalTicks) {
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
                    cancel();
                    activeRechargeDisplays.remove(playerId);
                    rechargeProgress.remove(playerId);
                    return;
                }

                double progressPercent = (double) progress / totalTicks;
                int charsFilled = (int) (progressPercent * PROGRESS_BAR.length());

                StringBuilder bar = new StringBuilder("§6Strafe: §a");
                for (int i = 0; i < charsFilled; i++) {
                    bar.append("|");
                }
                bar.append("§7");
                for (int i = charsFilled; i < PROGRESS_BAR.length(); i++) {
                    bar.append("|");
                }

                int secondsRemaining = COOLDOWN_SECONDS - (progress / 20);
                bar.append(" §e").append(secondsRemaining).append("s");

                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(bar.toString()));

                rechargeProgress.put(playerId, progress + 1);
            }
        }.runTaskTimer(plugin, 0L, 1L);

        activeRechargeDisplays.put(playerId, task);
    }

    public void cleanupPlayer(UUID playerId) {
        BukkitTask task = activeRechargeDisplays.remove(playerId);
        if (task != null) {
            task.cancel();
        }
        rechargeProgress.remove(playerId);
        strafeCooldowns.remove(playerId);
        fallDamageProtection.remove(playerId);
    }

    public boolean hasFallDamageProtection(UUID playerId) {
        Long protectionUntil = fallDamageProtection.get(playerId);
        if (protectionUntil == null) {
            return false;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime > protectionUntil) {
            fallDamageProtection.remove(playerId);
            return false;
        }

        return true;
    }

    public void removeFallProtection(UUID playerId) {
        fallDamageProtection.remove(playerId);
    }
}
