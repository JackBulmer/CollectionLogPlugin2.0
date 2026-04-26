package collectionlog.commands;

import collectionlog.CollectionLogPlugin;
import collectionlog.perks.PerkManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Handles the /craft command, opening a virtual crafting table for players
 * who have the {@code craft_command} perk unlocked (requires completing the
 * Utilities collection).
 *
 * <p>Crafting tracking continues to work because Bukkit fires the normal
 * {@code CraftItemEvent} regardless of whether the crafting inventory was
 * opened via a physical block or this virtual method.
 */
public class CraftCommand implements CommandExecutor {

    private final PerkManager perkManager;

    public CraftCommand(CollectionLogPlugin plugin) {
        this.perkManager = plugin.getPerkManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return true;
        }

        if (!perkManager.hasUnlocked(player.getUniqueId(), "craft_command")) {
            player.sendMessage("§cYou must complete the Utilities collection to use this command!");
            return true;
        }
        // craft_command is non-toggleable: hasUnlocked is the only check needed.

        player.openWorkbench(null, true);
        return true;
    }
}
