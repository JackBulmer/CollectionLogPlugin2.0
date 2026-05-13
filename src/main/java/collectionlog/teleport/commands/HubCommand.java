package collectionlog.teleport.commands;

import collectionlog.CollectionLogPlugin;
import collectionlog.teleport.HubManager;
import collectionlog.teleport.TeleportManager;
import collectionlog.teleport.TeleportSettingsManager;
import collectionlog.teleport.TeleportValidator;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;

public class HubCommand implements CommandExecutor {
    private final CollectionLogPlugin plugin;
    private final HubManager hubManager;
    private final TeleportManager teleportManager;
    private final TeleportSettingsManager teleportSettingsManager;
    private final TeleportValidator teleportValidator;

    public HubCommand(CollectionLogPlugin plugin, HubManager hubManager, TeleportManager teleportManager,
                      TeleportSettingsManager teleportSettingsManager, TeleportValidator teleportValidator) {
        this.plugin = plugin;
        this.hubManager = hubManager;
        this.teleportManager = teleportManager;
        this.teleportSettingsManager = teleportSettingsManager;
        this.teleportValidator = teleportValidator;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return true;
        }

        if (!teleportValidator.hasRequiredCollection(player)) {
            player.sendMessage("§cComplete the Tutorial Collection to unlock teleports");
            return true;
        }

        if (teleportValidator.isBannedWorld(player.getWorld().getName())) {
            player.sendMessage("§cYou cannot teleport right now");
            return true;
        }

        if (teleportManager.hasActiveTeleport(player.getUniqueId())) {
            player.sendMessage("§cTeleport already in progress");
            return true;
        }

        int hubCost = Math.max(0, plugin.getConfig().getInt("teleport.hub_cost", 1));

        hubManager.getHub().thenCompose(hubLocation -> {
            if (hubLocation == null) {
                plugin.getAsyncTaskManager().runSync(() -> player.sendMessage("§cHub has not been set yet."));
                return CompletableFuture.completedFuture(null);
            }

            if (teleportValidator.isBannedWorld(hubLocation.getWorld().getName())) {
                plugin.getAsyncTaskManager().runSync(() -> player.sendMessage("§cHub is set in a blocked world."));
                return CompletableFuture.completedFuture(null);
            }

            return teleportSettingsManager.getSettings(player.getUniqueId()).thenAccept(settings ->
                    plugin.getAsyncTaskManager().runSync(() ->
                            attemptTeleport(player, hubLocation, settings.getCountdownSeconds(), settings.isInterDimensional(), hubCost)));
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Error in /hub: " + ex.getMessage());
            return null;
        });

        return true;
    }

    private void attemptTeleport(Player player, Location destination, int countdownSeconds, boolean allowInterDimensional, int hubCost) {
        if (!allowInterDimensional && teleportValidator.isCrossDimension(player.getLocation(), destination)) {
            player.sendMessage("§cYou cannot teleport to other dimensions");
            return;
        }

        if (!teleportValidator.hasSufficientXP(player, hubCost)) {
            player.sendMessage("§cYou need " + hubCost + " XP levels to teleport (you have " + player.getLevel() + ")");
            return;
        }

        teleportManager.startTeleport(player, destination, countdownSeconds, hubCost, player);
    }
}
