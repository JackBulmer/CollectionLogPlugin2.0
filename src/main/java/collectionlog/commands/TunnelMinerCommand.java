package collectionlog.commands;

import collectionlog.CollectionLogPlugin;
import collectionlog.perks.PerkManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Command to toggle the Tunnel Miner perk on or off.
 *
 * <pre>
 * /tunnelminer  – toggle the perk
 * </pre>
 *
 * <p>Aliases: {@code /tunnel}, {@code /tmine}
 */
public class TunnelMinerCommand implements CommandExecutor {

    private static final String PERK_ID = "tunnel_miner";

    private final PerkManager perkManager;

    public TunnelMinerCommand(CollectionLogPlugin plugin) {
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
            player.sendMessage("§cYou haven't unlocked the Tunnel Miner perk yet!");
            player.sendMessage("§7Complete the Stones collection to unlock this perk.");
            return true;
        }

        boolean newState = perkManager.togglePerk(playerId, PERK_ID);

        if (newState) {
            player.sendMessage("§a§l✓ Tunnel Miner enabled!");
            player.sendMessage("§7Break a block with a pickaxe or shovel to mine a 3×3 area.");
        } else {
            player.sendMessage("§c§l✗ Tunnel Miner disabled.");
        }

        return true;
    }
}
