package collectionlog.commands;

import collectionlog.CollectionLogPlugin;
import collectionlog.leaderboard.LeaderboardManager;
import collectionlog.leaderboard.PlayerStats;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class LogStatsCommand implements CommandExecutor {
    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getIntegerInstance(Locale.US);

    private final CollectionLogPlugin plugin;
    private final LeaderboardManager leaderboardManager;

    public LogStatsCommand(CollectionLogPlugin plugin) {
        this.plugin = plugin;
        this.leaderboardManager = plugin.getLeaderboardManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        CompletableFuture<List<PlayerStats>> topPlayersFuture = leaderboardManager.getTopPlayers(10);
        CompletableFuture<LeaderboardManager.PlayerRank> playerRankFuture =
                sender instanceof Player player
                        ? leaderboardManager.getPlayerRank(player.getUniqueId())
                        : CompletableFuture.completedFuture(null);

        topPlayersFuture.thenCombine(playerRankFuture, Result::new)
                .thenAccept(result -> plugin.getAsyncTaskManager().runSync(() -> sendOutput(sender, result)))
                .exceptionally(throwable -> {
                    plugin.getLogger().warning("Failed to load /logstats leaderboard: " + throwable.getMessage());
                    plugin.getAsyncTaskManager().runSync(() -> sender.sendMessage("§cFailed to load collection log rankings."));
                    return null;
                });
        return true;
    }

    private void sendOutput(CommandSender sender, Result result) {
        sender.sendMessage("§6§l=== Top 10 Collection Log Rankings ===");

        if (result.topPlayers().isEmpty()) {
            sender.sendMessage("§7No ranked players yet.");
        } else {
            for (int i = 0; i < result.topPlayers().size(); i++) {
                PlayerStats playerStats = result.topPlayers().get(i);
                sender.sendMessage("§6" + (i + 1) + ". §f" + playerStats.username() + " §7- §e"
                        + NUMBER_FORMAT.format(playerStats.totalCollected()) + " items");
            }
        }

        if (!(sender instanceof Player) || result.playerRank() == null) {
            return;
        }

        LeaderboardManager.PlayerRank playerRank = result.playerRank();
        if (playerRank.ranked() && playerRank.rank() <= 10) {
            return;
        }

        sender.sendMessage("§7---");
        if (!playerRank.ranked() || playerRank.playerStats().totalCollected() <= 0) {
            sender.sendMessage("§7Your rank: Unranked - 0 items");
            return;
        }

        sender.sendMessage("§7Your rank: #" + playerRank.rank()
                + " - " + playerRank.playerStats().username()
                + " - " + NUMBER_FORMAT.format(playerRank.playerStats().totalCollected()) + " items");
    }

    private record Result(List<PlayerStats> topPlayers, LeaderboardManager.PlayerRank playerRank) {
    }
}
