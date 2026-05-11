package collectionlog.teleport.commands;

import collectionlog.CollectionLogPlugin;
import collectionlog.teleport.TeleportValidator;
import collectionlog.teleport.gui.HomesGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class HomesCommand implements CommandExecutor {
    private final CollectionLogPlugin plugin;
    private final HomesGUI homesGUI;
    private final TeleportValidator validator;

    public HomesCommand(CollectionLogPlugin plugin, HomesGUI homesGUI, TeleportValidator validator) {
        this.plugin = plugin;
        this.homesGUI = homesGUI;
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

        homesGUI.open(player);
        return true;
    }
}
