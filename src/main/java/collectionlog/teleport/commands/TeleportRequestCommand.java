package collectionlog.teleport.commands;

import collectionlog.CollectionLogPlugin;
import collectionlog.teleport.TeleportRequestManager;
import collectionlog.teleport.TeleportValidator;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TeleportRequestCommand implements CommandExecutor, TabCompleter {
    private final CollectionLogPlugin plugin;
    private final TeleportRequestManager requestManager;
    private final TeleportValidator validator;

    public TeleportRequestCommand(CollectionLogPlugin plugin, TeleportRequestManager requestManager,
                                   TeleportValidator validator) {
        this.plugin = plugin;
        this.requestManager = requestManager;
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
            player.sendMessage("§cUsage: /tpr <player>");
            return true;
        }

        if (validator.isBannedWorld(player.getWorld().getName())) {
            player.sendMessage("§cYou cannot request teleports in this dimension");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage("§cThat player is not online");
            return true;
        }

        if (target.equals(player)) {
            player.sendMessage("§cYou cannot send a request to yourself");
            return true;
        }

        if (validator.isBannedWorld(target.getWorld().getName())) {
            player.sendMessage("§cYou cannot teleport to them right now");
            return true;
        }

        requestManager.addRequest(player.getUniqueId(), target.getUniqueId());

        long timeout = plugin.getConfig().getLong("teleport.request_timeout", 60);
        target.sendMessage("§e" + player.getName() + " §7wants to teleport to you. Use §a/tpa §7to accept. §7(Expires in " + timeout + "s)");
        player.sendMessage("§aTeleport request sent to §e" + target.getName());

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(partial))
                .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
