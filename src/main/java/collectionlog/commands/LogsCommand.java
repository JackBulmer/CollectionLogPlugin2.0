package collectionlog.commands;

import collectionlog.CollectionLogPlugin;
import collectionlog.collections.CollectionDefinition;
import collectionlog.collections.SubCollectionDefinition;
import collectionlog.gui.CollectionGUI;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Handles /logs command execution and tab completion.
 */
public class LogsCommand implements CommandExecutor, TabCompleter {
    private final CollectionGUI collectionGUI;
    private final CollectionLogPlugin plugin;

    public LogsCommand(CollectionLogPlugin plugin) {
        this.plugin = plugin;
        this.collectionGUI = plugin.getCollectionGUI();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        if (args.length == 0) {
            collectionGUI.openMainMenu(player);
            return true;
        }

        String query = String.join(" ", args).trim();
        boolean opened = collectionGUI.openByQuery(player, query);
        if (!opened) {
            player.sendMessage(ChatColor.RED + "Collection or sub-collection not found: " + query);
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }

        String input = args[0].toLowerCase(Locale.ROOT);
        List<String> completions = new ArrayList<>();

        for (String suggestion : buildSuggestions()) {
            if (suggestion.startsWith(input)) {
                completions.add(suggestion);
            }
        }

        return completions;
    }

    private List<String> buildSuggestions() {
        Set<String> suggestions = new TreeSet<>();

        for (String id : plugin.getCollectionLoader().getCollections().keySet()) {
            addAliases(suggestions, id);
        }

        for (String id : plugin.getSpecialCollectionLoader().getSpecialCollections().keySet()) {
            addAliases(suggestions, id);
        }

        for (CollectionDefinition collection : plugin.getCollectionLoader().getCollections().values()) {
            for (SubCollectionDefinition subCollection : collection.getSubCollections()) {
                addAliases(suggestions, subCollection.getId());
                addAliases(suggestions, subCollection.getName());
                addAliases(suggestions, shortSubName(subCollection.getId()));
            }
        }

        return new ArrayList<>(suggestions);
    }

    private void addAliases(Set<String> suggestions, String value) {
        if (value == null || value.isBlank()) {
            return;
        }

        String normalized = value.toLowerCase(Locale.ROOT).trim();
        suggestions.add(normalized);
        suggestions.add(normalized.replace(' ', '_'));
        suggestions.add(normalized.replace('_', ' '));
        suggestions.add(normalized.replace("_", "").replace(" ", ""));
    }

    private String shortSubName(String subId) {
        int separator = subId.indexOf('_');
        if (separator >= 0 && separator < subId.length() - 1) {
            return subId.substring(separator + 1);
        }
        return subId;
    }
}
