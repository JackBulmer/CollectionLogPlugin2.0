package collectionlog.teleport.commands;

import collectionlog.CollectionLogPlugin;
import collectionlog.teleport.HomeManager;
import collectionlog.teleport.TeleportManager;
import collectionlog.teleport.TeleportSettingsManager;
import collectionlog.teleport.TeleportValidator;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class HomeCommand implements CommandExecutor, TabCompleter {
    private final CollectionLogPlugin plugin;
    private final HomeManager homeManager;
    private final TeleportManager teleportManager;
    private final TeleportSettingsManager settingsManager;
    private final TeleportValidator validator;

    public HomeCommand(CollectionLogPlugin plugin, HomeManager homeManager, TeleportManager teleportManager,
                       TeleportSettingsManager settingsManager, TeleportValidator validator) {
        this.plugin = plugin;
        this.homeManager = homeManager;
        this.teleportManager = teleportManager;
        this.settingsManager = settingsManager;
        this.validator = validator;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return true;
        }

        if (!validator.hasRequiredCollection(player)) {
            player.sendMessage("§cComplete the Tutorial Collection to unlock teleports");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("§cUsage: /home <name> | /home update <name> <material>");
            return true;
        }

        // /home update <name> <material>
        if (args[0].equalsIgnoreCase("update")) {
            if (args.length < 3) {
                player.sendMessage("§cUsage: /home update <name> <material>");
                return true;
            }
            String name = args[1];
            Material material;
            try {
                material = Material.valueOf(args[2].toUpperCase());
            } catch (IllegalArgumentException e) {
                player.sendMessage("§cInvalid material");
                return true;
            }
            final Material finalMat = material;
            homeManager.homeExists(player.getUniqueId(), name).thenCompose(exists -> {
                if (!exists) {
                    player.sendMessage("§cHome not found");
                    return CompletableFuture.completedFuture(null);
                }
                return homeManager.updateHomeMaterial(player.getUniqueId(), name, finalMat)
                    .thenRun(() -> plugin.getServer().getScheduler().runTask(plugin, () ->
                        player.sendMessage("§aHome '" + name + "' material updated to " + finalMat.name())));
            }).exceptionally(ex -> {
                plugin.getLogger().severe("Error updating home material: " + ex.getMessage());
                return null;
            });
            return true;
        }

        // /home <name>
        String name = args[0];

        if (validator.isBannedWorld(player.getWorld().getName())) {
            player.sendMessage("§cYou cannot teleport home right now");
            return true;
        }

        if (teleportManager.hasActiveTeleport(player.getUniqueId())) {
            player.sendMessage("§cTeleport already in progress");
            return true;
        }

        homeManager.getHome(player.getUniqueId(), name).thenCompose(home -> {
            if (home == null) {
                player.sendMessage("§cHome not found");
                return CompletableFuture.completedFuture(null);
            }
            Location dest = home.toLocation();
            if (dest == null) {
                player.sendMessage("§cHome world not found");
                return CompletableFuture.completedFuture(null);
            }

            if (validator.isBannedWorld(home.getWorld())) {
                player.sendMessage("§cYou cannot teleport home right now");
                return CompletableFuture.completedFuture(null);
            }

            return settingsManager.getSettings(player.getUniqueId()).thenAccept(settings -> {
                if (!settings.isInterDimensional() && validator.isCrossDimension(player.getLocation(), dest)) {
                    player.sendMessage("§cYou cannot teleport to other dimensions");
                    return;
                }

                if (!validator.hasSufficientXP(player, settings.getTpCost())) {
                    player.sendMessage("§cYou need " + settings.getTpCost() + " XP levels to teleport (you have " + player.getLevel() + ")");
                    return;
                }

                plugin.getServer().getScheduler().runTask(plugin, () ->
                    teleportManager.startTeleport(player, dest, settings.getCountdownSeconds(), settings.getTpCost(), player));
            });
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Error in /home: " + ex.getMessage());
            return null;
        });

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) return new ArrayList<>();
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> completions = new ArrayList<>();
            completions.add("update");
            try {
                homeManager.getHomeNames(player.getUniqueId()).get().stream()
                    .filter(n -> n.toLowerCase().startsWith(partial))
                    .forEach(completions::add);
            } catch (Exception ignored) {}
            return completions;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("update")) {
            String partial = args[1].toLowerCase();
            try {
                return homeManager.getHomeNames(player.getUniqueId()).get().stream()
                    .filter(n -> n.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
            } catch (Exception ignored) {}
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("update")) {
            String partial = args[2].toUpperCase();
            return Arrays.stream(Material.values())
                .map(Material::name)
                .filter(m -> m.startsWith(partial))
                .limit(20)
                .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
