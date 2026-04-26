package collectionlog.commands;

import collectionlog.CollectionLogPlugin;
import collectionlog.gui.PerksGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Handles the /perks command, opening the perks management GUI.
 */
public class PerksCommand implements CommandExecutor {

    private final PerksGUI perksGUI;

    public PerksCommand(CollectionLogPlugin plugin) {
        this.perksGUI = plugin.getPerksGUI();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return true;
        }

        perksGUI.openMainPage(player);
        return true;
    }
}
