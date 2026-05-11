package collectionlog.teleport.commands;

import collectionlog.CollectionLogPlugin;
import collectionlog.teleport.HomeManager;
import collectionlog.teleport.TeleportValidator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class DelHomeCommand implements CommandExecutor, TabCompleter {
    private final CollectionLogPlugin plugin;
    private final HomeManager homeManager;
    private final TeleportValidator validator;

    public DelHomeCommand(CollectionLogPlugin plugin, HomeManager homeManager, TeleportValidator validator) {
        this.plugin = plugin;
        this.homeManager = homeManager;
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
            player.sendMessage("§cUsage: /delhome <name>");
            return true;
        }

        String name = args[0];
        homeManager.homeExists(player.getUniqueId(), name).thenCompose(exists -> {
            if (!exists) {
                player.sendMessage("§cHome not found");
                return CompletableFuture.completedFuture(null);
            }
            return homeManager.deleteHome(player.getUniqueId(), name)
                .thenRun(() -> plugin.getServer().getScheduler().runTask(plugin, () ->
                    player.sendMessage("§aHome '" + name + "' deleted")));
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Error in /delhome: " + ex.getMessage());
            return null;
        });

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender instanceof Player player) {
            String partial = args[0].toLowerCase();
            try {
                return homeManager.getHomeNames(player.getUniqueId()).get().stream()
                    .filter(n -> n.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
            } catch (Exception ignored) {}
        }
        return new ArrayList<>();
    }
}
