package collectionlog.commands;

import collectionlog.CollectionLogPlugin;
import collectionlog.gui.TagsGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command to open the tags GUI.
 */
public class TagsCommand implements CommandExecutor {
    private final TagsGUI tagsGUI;

    public TagsCommand(CollectionLogPlugin plugin) {
        this.tagsGUI = plugin.getTagsGUI();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return true;
        }

        tagsGUI.openTagsGUI(player);
        return true;
    }
}
