package collectionlog.commands;

import collectionlog.CollectionLogPlugin;
import collectionlog.perks.PerkManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Handles the /dye command.
 *
 * <p>Opens the Infinite Dyes GUI for players who have unlocked the
 * {@code infinite_dyes} perk (requires completing the Dyes collection).
 */
public class DyeCommand implements CommandExecutor {

    private final CollectionLogPlugin plugin;
    private final PerkManager perkManager;

    public DyeCommand(CollectionLogPlugin plugin) {
        this.plugin = plugin;
        this.perkManager = plugin.getPerkManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return true;
        }

        if (!perkManager.hasUnlocked(player.getUniqueId(), "infinite_dyes")) {
            player.sendMessage("§cYou must complete the Dyes collection to use the Infinite Dyes perk!");
            return true;
        }

        plugin.getInfiniteDyesGUI().open(player, false);
        return true;
    }
}
