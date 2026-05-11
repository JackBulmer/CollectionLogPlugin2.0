package collectionlog.teleport.commands;

import collectionlog.CollectionLogPlugin;
import collectionlog.teleport.HomeManager;
import collectionlog.teleport.TeleportManager;
import collectionlog.teleport.TeleportSettingsManager;
import collectionlog.teleport.TeleportValidator;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TeleportOverrideCommand implements CommandExecutor, TabCompleter {
    private final CollectionLogPlugin plugin;
    private final HomeManager homeManager;
    private final TeleportManager teleportManager;
    private final TeleportSettingsManager settingsManager;
    private final TeleportValidator validator;

    public TeleportOverrideCommand(CollectionLogPlugin plugin, HomeManager homeManager,
                                    TeleportManager teleportManager, TeleportSettingsManager settingsManager,
                                    TeleportValidator validator) {
        this.plugin = plugin;
        this.homeManager = homeManager;
        this.teleportManager = teleportManager;
        this.settingsManager = settingsManager;
        this.validator = validator;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return false; // Let Bukkit handle non-player /tp
        }

        if (args.length == 0) {
            player.sendMessage("§cUsage: /tp <home|player>");
            return true;
        }

        // Try home lookup first for players with the required collection
        if (player.hasPermission("collectionlog.teleport.home") && validator.hasRequiredCollection(player)) {
            String name = args[0];
            homeManager.getHome(player.getUniqueId(), name).thenAccept(home -> {
                if (home != null) {
                    Location dest = home.toLocation();
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (dest == null) {
                            player.sendMessage("§cHome world not found");
                            return;
                        }
                        if (validator.isBannedWorld(player.getWorld().getName()) || validator.isBannedWorld(home.getWorld())) {
                            player.sendMessage("§cYou cannot teleport home right now");
                            return;
                        }
                        settingsManager.getSettings(player.getUniqueId()).thenAccept(settings ->
                            plugin.getServer().getScheduler().runTask(plugin, () -> {
                                if (!settings.isInterDimensional() && validator.isCrossDimension(player.getLocation(), dest)) {
                                    player.sendMessage("§cYou cannot teleport to other dimensions");
                                    return;
                                }
                                if (!validator.hasSufficientXP(player, settings.getTpCost())) {
                                    player.sendMessage("§cYou need " + settings.getTpCost() + " XP levels to teleport (you have " + player.getLevel() + ")");
                                    return;
                                }
                                if (teleportManager.hasActiveTeleport(player.getUniqueId())) {
                                    player.sendMessage("§cTeleport already in progress");
                                    return;
                                }
                                teleportManager.startTeleport(player, dest, settings.getCountdownSeconds(), settings.getTpCost(), player);
                            })
                        );
                    });
                } else if (sender.hasPermission("minecraft.command.tp")) {
                    // Home not found - vanilla fallback cannot be triggered here (already returned true)
                    plugin.getServer().getScheduler().runTask(plugin, () ->
                        player.sendMessage("§cHome not found: " + name));
                } else {
                    plugin.getServer().getScheduler().runTask(plugin, () ->
                        player.sendMessage("§cYou don't have permission to use this command."));
                }
            }).exceptionally(ex -> {
                plugin.getLogger().severe("Error in /tp: " + ex.getMessage());
                return null;
            });
            return true;
        }

        // Fall back to vanilla /tp for operators (no home permission or collection lock)
        if (sender.hasPermission("minecraft.command.tp")) {
            return false;
        }

        player.sendMessage("§cYou don't have permission to use this command.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender instanceof Player player) {
            String partial = args[0].toLowerCase();
            try {
                return homeManager.getHomeNames(player.getUniqueId())
                    .get(1, java.util.concurrent.TimeUnit.SECONDS).stream()
                    .filter(n -> n.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
            } catch (Exception ignored) {}
        }
        return new ArrayList<>();
    }
}
