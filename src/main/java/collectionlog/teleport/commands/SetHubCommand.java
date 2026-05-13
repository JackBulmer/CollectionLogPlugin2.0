package collectionlog.teleport.commands;

import collectionlog.CollectionLogPlugin;
import collectionlog.teleport.HubManager;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SetHubCommand implements CommandExecutor {
    private final CollectionLogPlugin plugin;
    private final HubManager hubManager;

    public SetHubCommand(CollectionLogPlugin plugin, HubManager hubManager) {
        this.plugin = plugin;
        this.hubManager = hubManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return true;
        }

        Location location = player.getLocation().clone();
        hubManager.setHub(location, player.getUniqueId())
                .thenRun(() -> plugin.getAsyncTaskManager().runSync(() ->
                        player.sendMessage("§aHub set to §e" + location.getWorld().getName()
                                + " §a(§e" + format(location.getX()) + "§a, §e"
                                + format(location.getY()) + "§a, §e" + format(location.getZ()) + "§a)")))
                .exceptionally(ex -> {
                    plugin.getLogger().severe("Error in /sethub: " + ex.getMessage());
                    plugin.getAsyncTaskManager().runSync(() ->
                            player.sendMessage("§cFailed to set hub location."));
                    return null;
                });
        return true;
    }

    private String format(double value) {
        return String.format("%.1f", value);
    }
}
