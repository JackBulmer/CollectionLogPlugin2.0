package collectionlog.teleport.commands;

import collectionlog.CollectionLogPlugin;
import collectionlog.teleport.HomeManager;
import collectionlog.teleport.TeleportSettingsManager;
import collectionlog.teleport.TeleportValidator;
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

public class SetHomeCommand implements CommandExecutor, TabCompleter {
    private final CollectionLogPlugin plugin;
    private final HomeManager homeManager;
    private final TeleportSettingsManager settingsManager;
    private final TeleportValidator validator;

    public SetHomeCommand(CollectionLogPlugin plugin, HomeManager homeManager,
                         TeleportSettingsManager settingsManager, TeleportValidator validator) {
        this.plugin = plugin;
        this.homeManager = homeManager;
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
            player.sendMessage("§cUsage: /sethome <name> [material]");
            return true;
        }

        String name = args[0];
        Material material = Material.RED_BED;

        if (args.length >= 2) {
            try {
                material = Material.valueOf(args[1].toUpperCase());
            } catch (IllegalArgumentException e) {
                player.sendMessage("§cInvalid material");
                return true;
            }
        }

        if (validator.isBannedWorld(player.getWorld().getName())) {
            player.sendMessage("§cYou cannot create a home in this dimension");
            return true;
        }

        final Material finalMaterial = material;
        settingsManager.getSettings(player.getUniqueId()).thenCompose(settings ->
            homeManager.countHomes(player.getUniqueId()).thenCompose(count -> {
                if (count >= settings.getMaxHomes()) {
                    player.sendMessage("§cYou have reached the maximum number of homes (" + settings.getMaxHomes() + ")");
                    return CompletableFuture.completedFuture(null);
                }
                return homeManager.homeExists(player.getUniqueId(), name).thenCompose(exists -> {
                    if (exists) {
                        player.sendMessage("§cA home with that name already exists");
                        return CompletableFuture.completedFuture(null);
                    }
                    return homeManager.saveHome(player.getUniqueId(), name, finalMaterial, player.getLocation())
                        .thenRun(() -> plugin.getServer().getScheduler().runTask(plugin, () ->
                            player.sendMessage("§aHome '" + name + "' created")));
                });
            })
        ).exceptionally(ex -> {
            plugin.getLogger().severe("Error in /sethome: " + ex.getMessage());
            return null;
        });

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 2) {
            String partial = args[1].toUpperCase();
            return Arrays.stream(Material.values())
                .map(Material::name)
                .filter(m -> m.startsWith(partial))
                .limit(20)
                .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
