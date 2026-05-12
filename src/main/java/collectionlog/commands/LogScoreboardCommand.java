package collectionlog.commands;

import collectionlog.CollectionLogPlugin;
import collectionlog.leaderboard.HologramManager;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LogScoreboardCommand implements CommandExecutor, TabCompleter {
    private final CollectionLogPlugin plugin;
    private final HologramManager hologramManager;

    public LogScoreboardCommand(CollectionLogPlugin plugin) {
        this.plugin = plugin;
        this.hologramManager = plugin.getHologramManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("collectionlog.admin.scoreboard")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage("§cUsage: /logscoreboard <create|remove>");
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        if ("create".equals(action)) {
            Location hologramLocation = player.getLocation().clone().add(0.0D, 0.5D, 0.0D);
            hologramManager.createHologram(hologramLocation)
                    .thenAccept(created -> plugin.getAsyncTaskManager().runSync(() ->
                            sender.sendMessage(created ? "§aScoreboard created." : "§cFailed to create scoreboard.")));
            return true;
        }

        if ("remove".equals(action)) {
            hologramManager.removeNearestHologram(player.getLocation())
                    .thenAccept(removed -> plugin.getAsyncTaskManager().runSync(() ->
                            sender.sendMessage(removed
                                    ? "§aScoreboard removed."
                                    : "§cNo scoreboard found within 10 blocks.")));
            return true;
        }

        sender.sendMessage("§cUsage: /logscoreboard <create|remove>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }

        List<String> completions = new ArrayList<>(List.of("create", "remove"));
        String input = args[0].toLowerCase(Locale.ROOT);
        completions.removeIf(option -> !option.startsWith(input));
        return completions;
    }
}
