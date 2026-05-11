package collectionlog.teleport.commands;

import collectionlog.CollectionLogPlugin;
import collectionlog.teleport.TeleportSettingsManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class TeleportAdminCommand {
    private final CollectionLogPlugin plugin;
    private final TeleportSettingsManager settingsManager;

    public TeleportAdminCommand(CollectionLogPlugin plugin, TeleportSettingsManager settingsManager) {
        this.plugin = plugin;
        this.settingsManager = settingsManager;
    }

    /**
     * Handle /cl teleport <player> <subcommand> [value]
     * args[0] = "teleport", args[1] = player, args[2] = subcommand, args[3] = value (optional)
     */
    public boolean handle(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /cl teleport <player> <max_homes|tp_cost|inter_dimensional|countdown|reset|view> [value]");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found (must be online): " + args[1]);
            return true;
        }

        UUID targetId = target.getUniqueId();
        String targetName = target.getName();
        String sub = args[2].toLowerCase();

        switch (sub) {
            case "view" -> settingsManager.getSettings(targetId).thenAccept(settings ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    sender.sendMessage("§eTeleport settings for §6" + targetName + "§e:");
                    sender.sendMessage("§7  max_homes: §f" + settings.getMaxHomes());
                    sender.sendMessage("§7  tp_cost: §f" + settings.getTpCost());
                    sender.sendMessage("§7  inter_dimensional: §f" + settings.isInterDimensional());
                    sender.sendMessage("§7  countdown_seconds: §f" + settings.getCountdownSeconds());
                }));
            case "reset" -> settingsManager.resetSettings(targetId).thenRun(() ->
                plugin.getServer().getScheduler().runTask(plugin, () ->
                    sender.sendMessage("§aTeleport settings reset to defaults for §6" + targetName)));
            case "max_homes" -> {
                if (args.length < 4) { sender.sendMessage("§cUsage: /cl teleport <player> max_homes <value>"); return true; }
                try {
                    int value = Integer.parseInt(args[3]);
                    settingsManager.getSettings(targetId).thenCompose(settings -> {
                        settings.setMaxHomes(value);
                        return settingsManager.saveSettings(targetId, settings);
                    }).thenRun(() -> plugin.getServer().getScheduler().runTask(plugin, () ->
                        sender.sendMessage("§aSet max_homes to §6" + value + "§a for §6" + targetName)));
                } catch (NumberFormatException e) { sender.sendMessage("§cInvalid number: " + args[3]); }
            }
            case "tp_cost" -> {
                if (args.length < 4) { sender.sendMessage("§cUsage: /cl teleport <player> tp_cost <value>"); return true; }
                try {
                    int value = Integer.parseInt(args[3]);
                    settingsManager.getSettings(targetId).thenCompose(settings -> {
                        settings.setTpCost(value);
                        return settingsManager.saveSettings(targetId, settings);
                    }).thenRun(() -> plugin.getServer().getScheduler().runTask(plugin, () ->
                        sender.sendMessage("§aSet tp_cost to §6" + value + "§a for §6" + targetName)));
                } catch (NumberFormatException e) { sender.sendMessage("§cInvalid number: " + args[3]); }
            }
            case "inter_dimensional" -> {
                if (args.length < 4) { sender.sendMessage("§cUsage: /cl teleport <player> inter_dimensional <true|false>"); return true; }
                boolean value = Boolean.parseBoolean(args[3]);
                settingsManager.getSettings(targetId).thenCompose(settings -> {
                    settings.setInterDimensional(value);
                    return settingsManager.saveSettings(targetId, settings);
                }).thenRun(() -> plugin.getServer().getScheduler().runTask(plugin, () ->
                    sender.sendMessage("§aSet inter_dimensional to §6" + value + "§a for §6" + targetName)));
            }
            case "countdown" -> {
                if (args.length < 4) { sender.sendMessage("§cUsage: /cl teleport <player> countdown <seconds>"); return true; }
                try {
                    int value = Integer.parseInt(args[3]);
                    settingsManager.getSettings(targetId).thenCompose(settings -> {
                        settings.setCountdownSeconds(value);
                        return settingsManager.saveSettings(targetId, settings);
                    }).thenRun(() -> plugin.getServer().getScheduler().runTask(plugin, () ->
                        sender.sendMessage("§aSet countdown to §6" + value + "§a for §6" + targetName)));
                } catch (NumberFormatException e) { sender.sendMessage("§cInvalid number: " + args[3]); }
            }
            default -> sender.sendMessage("§cUnknown subcommand. Use: max_homes, tp_cost, inter_dimensional, countdown, reset, view");
        }

        return true;
    }

    public List<String> onTabComplete(String[] args) {
        if (args.length == 2) {
            String partial = args[1].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(partial))
                .collect(Collectors.toList());
        }
        if (args.length == 3) {
            String partial = args[2].toLowerCase();
            return Arrays.asList("max_homes", "tp_cost", "inter_dimensional", "countdown", "reset", "view")
                .stream().filter(s -> s.startsWith(partial)).collect(Collectors.toList());
        }
        if (args.length == 4 && args[2].equalsIgnoreCase("inter_dimensional")) {
            return Arrays.asList("true", "false");
        }
        return new ArrayList<>();
    }
}
