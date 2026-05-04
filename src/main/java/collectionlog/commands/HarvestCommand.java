package collectionlog.commands;

import collectionlog.CollectionLogPlugin;
import collectionlog.perks.PerkManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Command to toggle the Harvest & Auto-Replant perk on or off.
 *
 * <pre>
 * /harvest  – toggle the perk
 * </pre>
 *
 * <p>Aliases: {@code /replant}, {@code /autoreplant}
 */
public class HarvestCommand implements CommandExecutor {

    private static final String PERK_ID = "harvest_and_replant";

    private final PerkManager perkManager;

    public HarvestCommand(CollectionLogPlugin plugin) {
        this.perkManager = plugin.getPerkManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return true;
        }

        UUID playerId = player.getUniqueId();

        if (!perkManager.hasUnlocked(playerId, PERK_ID)) {
            player.sendMessage("§cYou haven't unlocked the Harvest & Auto-Replant perk yet!");
            player.sendMessage("§7Complete the Hoes collection to unlock this perk.");
            return true;
        }

        boolean newState = perkManager.togglePerk(playerId, PERK_ID);

        if (newState) {
            player.sendMessage("§a§l✓ Harvest & Auto-Replant enabled!");
            player.sendMessage("§7Break fully grown crops with a hoe to auto-replant.");
        } else {
            player.sendMessage("§c§l✗ Harvest & Auto-Replant disabled.");
        }

        return true;
    }
}
