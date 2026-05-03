package collectionlog.commands;

import collectionlog.CollectionLogPlugin;
import collectionlog.perks.PerkManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Command to toggle the Vein Miner perk on or off.
 *
 * <pre>
 * /veinminer  – toggle the perk
 * </pre>
 *
 * <p>Aliases: {@code /vm}, {@code /vmine}
 */
public class VeinMinerCommand implements CommandExecutor {

    private static final String PERK_ID = "vein_miner";

    private final PerkManager perkManager;

    public VeinMinerCommand(CollectionLogPlugin plugin) {
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
            player.sendMessage("§cYou haven't unlocked the Vein Miner perk yet!");
            player.sendMessage("§7Complete the Ore Blocks sub collection to unlock this perk.");
            return true;
        }

        boolean newState = perkManager.togglePerk(playerId, PERK_ID);

        if (newState) {
            player.sendMessage("§a§l✓ Vein Miner enabled!");
            player.sendMessage("§7Break an ore block with a pickaxe to mine the entire vein.");
        } else {
            player.sendMessage("§c§l✗ Vein Miner disabled.");
        }

        return true;
    }
}
