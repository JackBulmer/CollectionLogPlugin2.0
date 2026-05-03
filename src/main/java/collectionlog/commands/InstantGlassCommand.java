package collectionlog.commands;

import collectionlog.CollectionLogPlugin;
import collectionlog.perks.PerkManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Command to toggle the Instant Glass Smelting perk on or off.
 *
 * <pre>
 * /instantglass  – toggle the perk
 * </pre>
 *
 * <p>Aliases: {@code /iglass}, {@code /glasssmelt}
 */
public class InstantGlassCommand implements CommandExecutor {

    private static final String PERK_ID = "instant_glass_smelting";

    private final PerkManager perkManager;

    public InstantGlassCommand(CollectionLogPlugin plugin) {
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
            player.sendMessage("§cYou haven't unlocked the Instant Glass Smelting perk yet!");
            player.sendMessage("§7Complete the Glass collection to unlock this perk.");
            return true;
        }

        boolean newState = perkManager.togglePerk(playerId, PERK_ID);

        if (newState) {
            player.sendMessage("§a§l✓ Instant Glass Smelting enabled!");
            player.sendMessage("§7Break sand with a shovel to get glass instantly.");
        } else {
            player.sendMessage("§c§l✗ Instant Glass Smelting disabled.");
        }

        return true;
    }
}
