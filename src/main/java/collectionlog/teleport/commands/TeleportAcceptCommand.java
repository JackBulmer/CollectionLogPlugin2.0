package collectionlog.teleport.commands;

import collectionlog.CollectionLogPlugin;
import collectionlog.teleport.TeleportManager;
import collectionlog.teleport.TeleportRequestManager;
import collectionlog.teleport.TeleportSettingsManager;
import collectionlog.teleport.TeleportValidator;
import collectionlog.teleport.data.TeleportRequest;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class TeleportAcceptCommand implements CommandExecutor, TabCompleter {
    private final CollectionLogPlugin plugin;
    private final TeleportRequestManager requestManager;
    private final TeleportManager teleportManager;
    private final TeleportSettingsManager settingsManager;
    private final TeleportValidator validator;

    public TeleportAcceptCommand(CollectionLogPlugin plugin, TeleportRequestManager requestManager,
                                  TeleportManager teleportManager, TeleportSettingsManager settingsManager,
                                  TeleportValidator validator) {
        this.plugin = plugin;
        this.requestManager = requestManager;
        this.teleportManager = teleportManager;
        this.settingsManager = settingsManager;
        this.validator = validator;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player accepter)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return true;
        }

        if (!validator.hasRequiredCollection(accepter)) {
            accepter.sendMessage("§cComplete the Tutorial Collection to unlock teleports");
            return true;
        }

        List<TeleportRequest> pending = requestManager.getPendingRequests(accepter.getUniqueId());

        if (pending.isEmpty()) {
            accepter.sendMessage("§cYou have no pending teleport requests");
            return true;
        }

        boolean chargeMe = args.length > 0 && args[args.length - 1].equalsIgnoreCase("charge-me");

        TeleportRequest request = null;
        if (args.length > 0 && !args[0].equalsIgnoreCase("charge-me")) {
            Player requester = Bukkit.getPlayerExact(args[0]);
            if (requester == null) {
                accepter.sendMessage("§cThat player is not online");
                return true;
            }
            request = requestManager.getRequest(requester.getUniqueId(), accepter.getUniqueId());
            if (request == null) {
                accepter.sendMessage("§cNo pending request from " + args[0]);
                return true;
            }
        } else {
            if (pending.size() > 1) {
                accepter.sendMessage("§cMultiple pending requests — specify a player name");
                return true;
            }
            request = pending.get(0);
        }

        Player requester = Bukkit.getPlayer(request.getRequesterId());
        if (requester == null || !requester.isOnline()) {
            requestManager.removeRequest(request.getRequesterId(), accepter.getUniqueId());
            accepter.sendMessage("§cThat player is no longer online");
            return true;
        }

        requestManager.removeRequest(request.getRequesterId(), accepter.getUniqueId());

        final TeleportRequest finalRequest = request;
        final Player finalRequester = requester;
        // chargeMe = accepter pays, otherwise requester pays
        Player costPlayer = chargeMe ? accepter : requester;

        settingsManager.getSettings(finalRequester.getUniqueId()).thenAccept(settings -> {
            int cost = settings.getTpCost();
            if (!validator.hasSufficientXP(costPlayer, cost)) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                    costPlayer.sendMessage("§cYou need " + cost + " XP levels to teleport (you have " + costPlayer.getLevel() + ")"));
                return;
            }

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (teleportManager.hasActiveTeleport(finalRequester.getUniqueId())) {
                    finalRequester.sendMessage("§cTeleport already in progress");
                    return;
                }
                teleportManager.startTeleport(finalRequester, accepter.getLocation(),
                    settings.getCountdownSeconds(), cost, costPlayer);
                accepter.sendMessage("§aTeleport accepted! " + finalRequester.getName() + " is teleporting to you.");
            });
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Error in /tpa: " + ex.getMessage());
            return null;
        });

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender instanceof Player player) {
            String partial = args[0].toLowerCase();
            List<TeleportRequest> pending = requestManager.getPendingRequests(player.getUniqueId());
            List<String> completions = pending.stream()
                .map(r -> Bukkit.getPlayer(r.getRequesterId()))
                .filter(Objects::nonNull)
                .map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(partial))
                .collect(Collectors.toList());
            if ("charge-me".startsWith(partial)) completions.add("charge-me");
            return completions;
        }
        return new ArrayList<>();
    }
}
