package collectionlog.commands;

import collectionlog.CollectionLogPlugin;
import collectionlog.managers.GlowingManager;
import collectionlog.perks.PerkManager;
import org.bukkit.DyeColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the /glowing command.
 *
 * <p>Requires the {@code glowing} perk to be unlocked (candles collection).
 *
 * <pre>
 * /glowing          – open colour selection GUI
 * /glowing on       – enable with last-used colour (default: WHITE)
 * /glowing off      – disable glowing
 * /glowing {@literal <color>}  – set colour and enable (tab-completes all 16 dye colours)
 * </pre>
 */
public class GlowingCommand implements CommandExecutor, TabCompleter {

    private static final List<String> COLOR_NAMES = buildColorNames();

    private final CollectionLogPlugin plugin;
    private final PerkManager perkManager;

    public GlowingCommand(CollectionLogPlugin plugin) {
        this.plugin = plugin;
        this.perkManager = plugin.getPerkManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return true;
        }

        if (!perkManager.hasUnlocked(player.getUniqueId(), "glowing")) {
            player.sendMessage("§cYou must complete the Candles collection to use the Glowing perk!");
            return true;
        }

        GlowingManager gm = plugin.getGlowingManager();

        if (args.length == 0) {
            // Open GUI
            plugin.getGlowingGUI().open(player, false);
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("on")) {
            gm.toggleGlow(player.getUniqueId(), true);
            String color = gm.getGlowColor(player.getUniqueId());
            player.sendMessage("§aGlowing enabled with colour §f" + formatColorName(color) + "§a.");
            return true;
        }

        if (sub.equals("off")) {
            gm.toggleGlow(player.getUniqueId(), false);
            player.sendMessage("§7Glowing disabled.");
            return true;
        }

        // Try to parse as a colour
        try {
            DyeColor color = DyeColor.valueOf(sub.toUpperCase());
            gm.setGlowColor(player.getUniqueId(), color.name());
            player.sendMessage("§aGlowing colour set to §f" + formatColorName(color.name()) + "§a.");
            return true;
        } catch (IllegalArgumentException e) {
            player.sendMessage("§cUnknown colour: §f" + args[0] + "§c. Use tab-complete to see valid colours.");
            return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            String input = args[0].toLowerCase();
            completions.add("on");
            completions.add("off");
            completions.addAll(COLOR_NAMES);
            completions.removeIf(s -> !s.toLowerCase().startsWith(input));
            return completions;
        }
        return List.of();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static List<String> buildColorNames() {
        List<String> names = new ArrayList<>();
        for (DyeColor color : DyeColor.values()) {
            names.add(color.name().toLowerCase());
        }
        return List.copyOf(names);
    }

    private String formatColorName(String color) {
        String raw = color.replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        for (String word : raw.split(" ")) {
            if (!word.isEmpty()) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) sb.append(word.substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }
}
