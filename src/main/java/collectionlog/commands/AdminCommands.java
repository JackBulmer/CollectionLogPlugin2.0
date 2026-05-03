package collectionlog.commands;

import collectionlog.CollectionLogPlugin;
import collectionlog.collections.CollectionDefinition;
import collectionlog.collections.SubCollectionDefinition;
import collectionlog.database.dao.PlayerCollectionDAO;
import collectionlog.managers.ProgressTrackingManager;
import collectionlog.milestones.MilestoneManager;
import collectionlog.models.PlayerCollectionProgress;
import collectionlog.perks.Perk;
import collectionlog.perks.PerkManager;
import collectionlog.perks.PerkRegistry;
import collectionlog.utils.MissingItemsReportGenerator;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;

public class AdminCommands implements CommandExecutor, TabCompleter {
    private final CollectionLogPlugin plugin;

    public AdminCommands(CollectionLogPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("collectionlog.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        try {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "collection" -> handleCollectionCommand(sender, args);
                case "item" -> handleItemCommand(sender, args);
                case "perk" -> handlePerkCommand(sender, args);
                case "reload" -> handleReload(sender);
                case "missing-items" -> handleMissingItems(sender);
                case "givelava" -> handleGiveLava(sender, args);
                default -> {
                    sendHelp(sender);
                    yield true;
                }
            };
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
            sender.sendMessage("§cCommand failed: " + cause.getMessage());
            plugin.getLogger().log(Level.WARNING, "Admin command failed", exception);
            return true;
        }
    }

    private boolean handleCollectionCommand(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§cUsage: /cl collection <complete|reset> <player> <collection[:subcollection]>");
            return true;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found: " + args[2]);
            return true;
        }

        String collectionQuery = String.join(" ", Arrays.copyOfRange(args, 3, args.length));

        if (collectionQuery.contains(":")) {
            return handleSubCollectionCommand(sender, target, collectionQuery, action);
        }

        CollectionDefinition collection = findCollectionByQuery(collectionQuery);
        if (collection == null) {
            sender.sendMessage("§cCollection not found: " + collectionQuery);
            return true;
        }

        return switch (action) {
            case "complete" -> completeCollection(sender, target, collection);
            case "reset" -> resetCollection(sender, target, collection);
            default -> {
                sender.sendMessage("§cInvalid action. Use 'complete' or 'reset'.");
                yield true;
            }
        };
    }

    private boolean handleSubCollectionCommand(CommandSender sender, Player target, String query, String action) {
        String[] parts = query.split(":", 2);
        if (parts.length != 2) {
            sender.sendMessage("§cInvalid format. Use: <collection>:<subcollection>");
            return true;
        }

        String parentName = parts[0].trim();
        String subName = parts[1].trim();

        CollectionDefinition parent = findCollectionByQuery(parentName);
        if (parent == null) {
            sender.sendMessage("§cParent collection not found: " + parentName);
            return true;
        }

        SubCollectionDefinition sub = parent.getSubCollections().stream()
                .filter(s -> normalizeIdentifier(s.getName()).equals(normalizeIdentifier(subName))
                        || normalizeIdentifier(s.getId()).equals(normalizeIdentifier(subName)))
                .findFirst()
                .orElse(null);

        if (sub == null) {
            sender.sendMessage("§cSub-collection '" + subName + "' not found in '" + parent.getName() + "'");
            return true;
        }

        return switch (action) {
            case "complete" -> completeSubCollection(sender, target, parent, sub);
            case "reset" -> resetSubCollection(sender, target, parent, sub);
            default -> {
                sender.sendMessage("§cInvalid action. Use 'complete' or 'reset'.");
                yield true;
            }
        };
    }

    private boolean completeCollection(CommandSender sender, Player target, CollectionDefinition collection) {
        ProgressTrackingManager progressTrackingManager = plugin.getProgressTrackingManager();
        MilestoneManager milestoneManager = plugin.getMilestoneManager();
        if (progressTrackingManager == null) {
            sender.sendMessage("§cProgress tracking is unavailable.");
            return true;
        }

        for (String signature : collection.getItems()) {
            joinFuture(progressTrackingManager.trackItemCollection(target, collection, signature, true));
        }

        if (milestoneManager != null) {
            milestoneManager.checkCollectionCompletion(target.getUniqueId(), collection.getId());

            for (SubCollectionDefinition sub : collection.getSubCollections()) {
                milestoneManager.checkSubCollectionCompletion(
                        target.getUniqueId(),
                        collection.getId(),
                        sub.getName()
                );
            }
        }

        sender.sendMessage("§aMarked collection '" + collection.getName() + "' as complete for " + target.getName());
        target.sendMessage("§a§lCollection completed by admin: " + collection.getName());
        return true;
    }

    private boolean completeSubCollection(CommandSender sender, Player target,
                                         CollectionDefinition parent, SubCollectionDefinition sub) {
        ProgressTrackingManager pm = plugin.getProgressTrackingManager();
        MilestoneManager mm = plugin.getMilestoneManager();

        if (pm == null) {
            sender.sendMessage("§cProgress tracking is unavailable.");
            return true;
        }

        for (String signature : sub.getItems()) {
            joinFuture(pm.trackItemCollection(target, parent, signature, true));
        }

        if (mm != null) {
            mm.checkSubCollectionCompletion(target.getUniqueId(), parent.getId(), sub.getName());
        }

        sender.sendMessage("§aCompleted '" + parent.getName() + " › " + sub.getName() + "' for " + target.getName());
        return true;
    }

    private boolean resetSubCollection(CommandSender sender, Player target,
                                      CollectionDefinition parent, SubCollectionDefinition sub) {
        PlayerCollectionDAO dao = plugin.getPlayerCollectionDAO();
        MilestoneManager mm = plugin.getMilestoneManager();
        UUID playerId = target.getUniqueId();

        for (String signature : sub.getItems()) {
            joinFuture(dao.removeCollectedItem(playerId, parent.getId(), signature));
        }
        joinFuture(dao.updateProgress(playerId, parent.getId()));

        if (mm != null) {
            String key = parent.getId() + ":" + sub.getName();
            mm.revokeCollectionMilestone(playerId, key);
        }

        sender.sendMessage("§aReset '" + parent.getName() + " › " + sub.getName() + "' for " + target.getName());
        return true;
    }

    private boolean resetCollection(CommandSender sender, Player target, CollectionDefinition collection) {
        PlayerCollectionDAO playerCollectionDAO = plugin.getPlayerCollectionDAO();
        MilestoneManager milestoneManager = plugin.getMilestoneManager();
        UUID playerId = target.getUniqueId();

        joinFuture(playerCollectionDAO.resetCollectionProgress(playerId, collection.getId()));
        if (milestoneManager != null) {
            milestoneManager.revokeCollectionMilestones(playerId, collection.getId());
        }

        sender.sendMessage("§aReset collection '" + collection.getName() + "' for " + target.getName());
        target.sendMessage("§cCollection reset by admin: " + collection.getName());
        return true;
    }

    private boolean handleItemCommand(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§cUsage: /cl item <add|remove> <player> <item_id>");
            return true;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found: " + args[2]);
            return true;
        }

        String itemId = args[3];
        List<ItemCollectionMatch> matches = findCollectionsWithItem(itemId);
        if (matches.isEmpty()) {
            sender.sendMessage("§cItem " + itemId + " is not in any collection.");
            return true;
        }

        return switch (action) {
            case "add" -> addItem(sender, target, matches, itemId);
            case "remove" -> removeItem(sender, target, matches, itemId);
            default -> {
                sender.sendMessage("§cInvalid action. Use 'add' or 'remove'.");
                yield true;
            }
        };
    }

    private boolean addItem(CommandSender sender, Player target, List<ItemCollectionMatch> matches, String itemId) {
        ProgressTrackingManager progressTrackingManager = plugin.getProgressTrackingManager();
        if (progressTrackingManager == null) {
            sender.sendMessage("§cProgress tracking is unavailable.");
            return true;
        }

        Set<String> affectedCollections = new LinkedHashSet<>();
        for (ItemCollectionMatch match : matches) {
            joinFuture(progressTrackingManager.trackItemCollection(target, match.collection(), match.signature()));
            affectedCollections.add(match.collection().getName());
        }

        sender.sendMessage("§aAdded " + itemId + " to collections for " + target.getName());
        sender.sendMessage("§7Affected collections: " + String.join(", ", affectedCollections));
        return true;
    }

    private boolean removeItem(CommandSender sender, Player target, List<ItemCollectionMatch> matches, String itemId) {
        PlayerCollectionDAO playerCollectionDAO = plugin.getPlayerCollectionDAO();
        MilestoneManager milestoneManager = plugin.getMilestoneManager();
        UUID playerId = target.getUniqueId();

        Set<String> affectedCollections = new LinkedHashSet<>();
        Set<String> revokedCollections = new LinkedHashSet<>();

        for (ItemCollectionMatch match : matches) {
            String collectionId = match.collection().getId();
            PlayerCollectionProgress previousProgress = joinFuture(playerCollectionDAO.getProgress(playerId, collectionId));
            boolean wasComplete = previousProgress != null && previousProgress.isComplete();

            boolean removed = joinFuture(playerCollectionDAO.removeCollectedItem(playerId, collectionId, match.signature()));
            if (!removed) {
                continue;
            }

            joinFuture(playerCollectionDAO.updateProgress(playerId, collectionId));
            PlayerCollectionProgress currentProgress = joinFuture(playerCollectionDAO.getProgress(playerId, collectionId));
            boolean nowComplete = currentProgress != null && currentProgress.isComplete();

            affectedCollections.add(match.collection().getName());
            if (wasComplete && !nowComplete) {
                joinFuture(playerCollectionDAO.clearCollectionCompletion(playerId, collectionId));
                if (milestoneManager != null) {
                    milestoneManager.revokeCollectionMilestone(playerId, collectionId);
                }
                revokedCollections.add(match.collection().getName());
            }
        }

        if (affectedCollections.isEmpty()) {
            sender.sendMessage("§eNo progress changed for " + itemId + ".");
            return true;
        }

        sender.sendMessage("§aRemoved " + itemId + " from collections for " + target.getName());
        sender.sendMessage("§7Affected collections: " + String.join(", ", affectedCollections));
        if (!revokedCollections.isEmpty()) {
            sender.sendMessage("§eRevoked collection perks: " + String.join(", ", revokedCollections));
        }
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        sender.sendMessage("§eReloading plugin configuration...");

        // Reload config.yml
        plugin.reloadConfig();
        sender.sendMessage("§a✓ Reloaded config.yml");

        // Reload milestones
        MilestoneManager milestoneManager = plugin.getMilestoneManager();
        if (milestoneManager != null) {
            milestoneManager.reload();
            sender.sendMessage("§a✓ Reloaded milestones.yml");
        }

        // Reload custom recipes
        if (plugin.getCustomRecipeManager() != null) {
            plugin.getCustomRecipeManager().loadRecipes();
            sender.sendMessage("§a✓ Reloaded custom recipes");
        }

        sender.sendMessage("§a§l✓ Reload complete!");
        return true;
    }

    private boolean handleMissingItems(CommandSender sender) {
        sender.sendMessage("§eGenerating missing items report, please wait...");
        MissingItemsReportGenerator generator = new MissingItemsReportGenerator(
                plugin,
                plugin.getCollectionLoader(),
                plugin.getSpecialCollectionLoader()
        );
        File report = generator.generate();
        if (report != null) {
            sender.sendMessage("§a✅ Missing items report generated: " + report.getName());
        } else {
            sender.sendMessage("§cFailed to generate missing items report. Check server logs for details.");
        }
        return true;
    }

    private CollectionDefinition findCollectionByQuery(String query) {
        String normalizedQuery = normalizeIdentifier(query);
        for (CollectionDefinition collection : getAllCollections().values()) {
            if (normalizeIdentifier(collection.getId()).equals(normalizedQuery)
                    || normalizeIdentifier(collection.getName()).equals(normalizedQuery)) {
                return collection;
            }
        }
        return null;
    }

    private List<ItemCollectionMatch> findCollectionsWithItem(String itemId) {
        List<ItemCollectionMatch> matches = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        String normalizedItem = normalizeIdentifier(itemId);
        Material material = Material.matchMaterial(itemId);

        for (CollectionDefinition collection : getAllCollections().values()) {
            for (String signature : collection.getItems()) {
                if (!matchesItemId(signature, itemId, normalizedItem, material)) {
                    continue;
                }

                String key = collection.getId() + "|" + signature;
                if (seen.add(key)) {
                    matches.add(new ItemCollectionMatch(collection, signature));
                }
            }
        }
        return matches;
    }

    private boolean matchesItemId(String signature, String itemId, String normalizedItem, Material material) {
        if (signature.equalsIgnoreCase(itemId)) {
            return true;
        }

        if (normalizeIdentifier(signature).equals(normalizedItem)) {
            return true;
        }

        return material != null && signature.equalsIgnoreCase(material.name());
    }

    private Map<String, CollectionDefinition> getAllCollections() {
        Map<String, CollectionDefinition> allCollections = new LinkedHashMap<>();
        allCollections.putAll(plugin.getCollectionLoader().getCollections());
        allCollections.putAll(plugin.getSpecialCollectionLoader().getSpecialCollections());
        return allCollections;
    }

    private String normalizeIdentifier(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replace(' ', '_')
                .replaceAll("_+", "_");
    }

    // ── Perk admin commands ───────────────────────────────────────────────────

    private boolean handlePerkCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /cl perk <unlock|revoke|toggle|enable|disable> <player> <perk_id>");
            return true;
        }

        String action = args[1].toLowerCase(Locale.ROOT);

        if (args.length < 4) {
            sender.sendMessage("§cUsage: /cl perk " + action + " <player> <perk_id>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found or not online: " + args[2]);
            return true;
        }

        String perkId = args[3].toLowerCase(Locale.ROOT);
        Perk perk = PerkRegistry.getPerk(perkId);
        if (perk == null) {
            sender.sendMessage("§cUnknown perk: §e" + perkId);
            sender.sendMessage("§7Valid perks: " + String.join(", ",
                    PerkRegistry.getAllPerks().stream().map(Perk::getId).toList()));
            return true;
        }

        PerkManager perkManager = plugin.getPerkManager();

        switch (action) {
            case "unlock" -> {
                perkManager.unlockPerk(target.getUniqueId(), perkId);
                sender.sendMessage("§aUnlocked perk §e" + perk.getName() + " §afor §e" + target.getName());
                target.sendMessage("§a✦ Perk unlocked: §e" + perk.getName());
            }
            case "revoke" -> {
                perkManager.revokePerk(target.getUniqueId(), perkId);
                sender.sendMessage("§eRevoked perk §c" + perk.getName() + " §efrom §c" + target.getName());
                target.sendMessage("§c✦ Perk revoked: §e" + perk.getName());
            }
            case "toggle" -> {
                if (!perk.isToggleable()) {
                    sender.sendMessage("§cPerk §e" + perk.getName() + " §cis not toggleable.");
                    return true;
                }
                if (!perkManager.hasUnlocked(target.getUniqueId(), perkId)) {
                    sender.sendMessage("§c" + target.getName() + " does not have perk §e" + perk.getName() + " §cunlocked.");
                    return true;
                }
                boolean newState = perkManager.togglePerk(target.getUniqueId(), perkId);
                String stateColour = newState ? "§a" : "§c";
                String stateWord   = newState ? "enabled" : "disabled";
                sender.sendMessage("§aPerk §e" + perk.getName() + " §afor §e" + target.getName()
                        + " §ais now " + stateColour + stateWord + "§a.");
            }
            case "enable" -> {
                if (!perk.isToggleable()) {
                    sender.sendMessage("§cPerk §e" + perk.getName() + " §cis not toggleable.");
                    return true;
                }
                perkManager.setEnabled(target.getUniqueId(), perkId, true);
                sender.sendMessage("§aEnabled perk §e" + perk.getName() + " §afor §e" + target.getName());
            }
            case "disable" -> {
                if (!perk.isToggleable()) {
                    sender.sendMessage("§cPerk §e" + perk.getName() + " §cis not toggleable.");
                    return true;
                }
                perkManager.setEnabled(target.getUniqueId(), perkId, false);
                sender.sendMessage("§eDisabled perk §e" + perk.getName() + " §efor §e" + target.getName());
            }
            default -> sender.sendMessage("§cUnknown perk action: " + action
                    + ". Use unlock, revoke, toggle, enable, or disable.");
        }
        return true;
    }

    private boolean handleGiveLava(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /cl givelava <player>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found or not online: " + args[1]);
            return true;
        }

        target.getInventory().addItem(plugin.getInfiniteLavaBucket().createItem());
        sender.sendMessage("§aGave infinite lava bucket to §e" + target.getName());
        target.sendMessage("§6✦ §eYou received: §6§lInfinite Lava Bucket");
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6§l=== Collection Log Admin Commands ===");
        sender.sendMessage("§e/cl collection complete <player> <collection>");
        sender.sendMessage("§7  - Mark collection as complete and award perks");
        sender.sendMessage("§e/cl collection complete <player> <collection>:<subcollection>");
        sender.sendMessage("§7  - Mark a specific sub-collection as complete");
        sender.sendMessage("§e/cl collection reset <player> <collection>");
        sender.sendMessage("§7  - Reset collection and revoke perks");
        sender.sendMessage("§e/cl collection reset <player> <collection>:<subcollection>");
        sender.sendMessage("§7  - Reset a specific sub-collection");
        sender.sendMessage("§e/cl item add <player> <item_id>");
        sender.sendMessage("§7  - Add item to all collections it appears in");
        sender.sendMessage("§e/cl item remove <player> <item_id>");
        sender.sendMessage("§7  - Remove item from all collections");
        sender.sendMessage("§e/cl perk unlock <player> <perk_id>");
        sender.sendMessage("§7  - Manually unlock a perk for a player");
        sender.sendMessage("§e/cl perk revoke <player> <perk_id>");
        sender.sendMessage("§7  - Manually revoke a perk from a player");
        sender.sendMessage("§e/cl perk toggle <player> <perk_id>");
        sender.sendMessage("§7  - Toggle a perk on/off for a player");
        sender.sendMessage("§e/cl reload");
        sender.sendMessage("§7  - Reload config.yml, milestones, and custom recipes");
        sender.sendMessage("§e/cl missing-items");
        sender.sendMessage("§7  - Generate missing_items.txt report of uncollected materials");
        sender.sendMessage("§e/cl givelava <player>");
        sender.sendMessage("§7  - Give an infinite lava bucket to a player");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("collectionlog.admin")) {
            return List.of();
        }

        if (args.length == 1) {
            return filterCompletions(args[0], List.of("collection", "item", "perk", "reload", "missing-items", "givelava"));
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("collection")) {
            return filterCompletions(args[1], List.of("complete", "reset"));
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("item")) {
            return filterCompletions(args[1], List.of("add", "remove"));
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("perk")) {
            return filterCompletions(args[1], List.of("unlock", "revoke", "toggle", "enable", "disable"));
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("givelava")) {
            List<String> players = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                players.add(player.getName());
            }
            return filterCompletions(args[1], players);
        }

        if (args.length == 3) {
            List<String> players = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                players.add(player.getName());
            }
            return filterCompletions(args[2], players);
        }

        if (args.length >= 4 && args[0].equalsIgnoreCase("collection")) {
            String query = String.join(" ", Arrays.copyOfRange(args, 3, args.length));

            if (query.contains(":")) {
                String[] parts = query.split(":", 2);
                String parentQuery = parts[0].trim();
                CollectionDefinition parent = findCollectionByQuery(parentQuery);
                if (parent != null && !parent.getSubCollections().isEmpty()) {
                    List<String> suggestions = new ArrayList<>();
                    for (SubCollectionDefinition sub : parent.getSubCollections()) {
                        suggestions.add(parent.getName() + ":" + sub.getName());
                    }
                    return filterCompletions(query, suggestions);
                }
                return List.of();
            }

            Set<String> collectionNames = new LinkedHashSet<>();
            for (CollectionDefinition collection : getAllCollections().values()) {
                collectionNames.add(collection.getName());
                collectionNames.add(collection.getId());
            }
            return filterCompletions(query, new ArrayList<>(collectionNames));
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("item")) {
            Set<String> itemIds = new LinkedHashSet<>();
            for (CollectionDefinition collection : getAllCollections().values()) {
                itemIds.addAll(collection.getItems());
            }
            return filterCompletions(args[3], new ArrayList<>(itemIds));
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("perk")) {
            List<String> perkIds = PerkRegistry.getAllPerks().stream()
                    .map(Perk::getId)
                    .collect(java.util.stream.Collectors.toList());
            return filterCompletions(args[3], perkIds);
        }

        return List.of();
    }

    private List<String> filterCompletions(String input, List<String> values) {
        String normalizedInput = input == null ? "" : input.toLowerCase(Locale.ROOT);
        List<String> filtered = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(normalizedInput)) {
                filtered.add(value);
            }
        }
        return filtered;
    }

    private <T> T joinFuture(CompletableFuture<T> future) {
        return future.join();
    }

    private record ItemCollectionMatch(CollectionDefinition collection, String signature) {
    }
}
