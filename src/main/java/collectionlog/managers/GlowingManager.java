package collectionlog.managers;

import collectionlog.CollectionLogPlugin;
import collectionlog.database.DatabaseType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages the Glowing perk: scoreboard team glow colours and DB persistence.
 *
 * <p>Team colour determines outline colour; the GLOWING potion effect makes the
 * outline visible. Both are applied/removed together whenever glow is toggled.
 */
public class GlowingManager implements Listener {

    private static final String DEFAULT_COLOR = "WHITE";
    /** Very long duration – effectively permanent until manually removed. */
    private static final int GLOW_DURATION = Integer.MAX_VALUE;

    private static final DyeColor[] COLOR_ORDER = {
            DyeColor.WHITE, DyeColor.ORANGE, DyeColor.MAGENTA, DyeColor.LIGHT_BLUE,
            DyeColor.YELLOW, DyeColor.LIME, DyeColor.PINK, DyeColor.GRAY,
            DyeColor.LIGHT_GRAY, DyeColor.CYAN, DyeColor.PURPLE, DyeColor.BLUE,
            DyeColor.BROWN, DyeColor.GREEN, DyeColor.RED, DyeColor.BLACK
    };

    private final CollectionLogPlugin plugin;
    private final Map<UUID, String> playerColors = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> playerGlowEnabled = new ConcurrentHashMap<>();
    private Scoreboard scoreboard;

    public GlowingManager(CollectionLogPlugin plugin) {
        this.plugin = plugin;
        initializeTeams();
    }

    // ── Team setup ────────────────────────────────────────────────────────────

    private void initializeTeams() {
        scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        for (DyeColor color : DyeColor.values()) {
            String teamName = "glow_" + color.name().toLowerCase();
            Team team = scoreboard.getTeam(teamName);
            if (team == null) {
                team = scoreboard.registerNewTeam(teamName);
            }
            team.setColor(dyeColorToChatColor(color));
            team.setAllowFriendlyFire(true);
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Set the glow colour for a player and enable glowing immediately.
     *
     * @param playerId UUID of the player
     * @param color    DyeColor name (case-insensitive)
     */
    public void setGlowColor(UUID playerId, String color) {
        String upper = color.toUpperCase();
        playerColors.put(playerId, upper);
        playerGlowEnabled.put(playerId, true);
        saveToDatabase(playerId, true, upper);

        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            applyGlow(player);
        }
    }

    /**
     * Enable or disable glowing for a player without changing their saved colour.
     */
    public void toggleGlow(UUID playerId, boolean enabled) {
        playerGlowEnabled.put(playerId, enabled);
        String color = playerColors.getOrDefault(playerId, DEFAULT_COLOR);
        saveToDatabase(playerId, enabled, color);

        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            if (enabled) {
                applyGlow(player);
            } else {
                removeGlow(player);
            }
        }
    }

    /** Returns the last-used glow colour for the player (defaults to WHITE). */
    public String getGlowColor(UUID playerId) {
        return playerColors.getOrDefault(playerId, DEFAULT_COLOR);
    }

    /** Returns true if the player currently has glowing enabled. */
    public boolean isGlowEnabled(UUID playerId) {
        return playerGlowEnabled.getOrDefault(playerId, false);
    }

    /** Apply current glow settings to an online player. */
    public void applyGlow(Player player) {
        UUID playerId = player.getUniqueId();
        String colorName = getGlowColor(playerId);

        // Remove from every glow team first to avoid duplicates
        for (DyeColor color : DyeColor.values()) {
            Team team = scoreboard.getTeam("glow_" + color.name().toLowerCase());
            if (team != null && team.hasEntry(player.getName())) {
                team.removeEntry(player.getName());
            }
        }

        // Join the team matching the chosen colour
        DyeColor dyeColor;
        try {
            dyeColor = DyeColor.valueOf(colorName);
        } catch (IllegalArgumentException e) {
            dyeColor = DyeColor.WHITE;
        }
        Team team = scoreboard.getTeam("glow_" + dyeColor.name().toLowerCase());
        if (team != null) {
            team.addEntry(player.getName());
        }

        // Reapply the GLOWING effect (replaces any previous instance)
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.GLOWING, GLOW_DURATION, 0, false, false, false));
    }

    /** Remove all glow-related state from an online player. */
    public void removeGlow(Player player) {
        for (DyeColor color : DyeColor.values()) {
            Team team = scoreboard.getTeam("glow_" + color.name().toLowerCase());
            if (team != null && team.hasEntry(player.getName())) {
                team.removeEntry(player.getName());
            }
        }
        player.removePotionEffect(PotionEffectType.GLOWING);
    }

    /**
     * Load glow settings for a player from the database (async) and apply them
     * if they are online and have glowing enabled.
     */
    public void loadPlayerData(UUID playerId) {
        plugin.getAsyncTaskManager().runAsync(() -> {
            String sql = "SELECT enabled, color FROM player_glowing WHERE player_uuid = ?";
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerId.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        boolean enabled = rs.getBoolean("enabled");
                        String color = rs.getString("color");
                        playerColors.put(playerId, color);
                        playerGlowEnabled.put(playerId, enabled);

                        if (enabled) {
                            plugin.getServer().getScheduler().runTask(plugin, () -> {
                                Player player = Bukkit.getPlayer(playerId);
                                if (player != null) {
                                    applyGlow(player);
                                }
                            });
                        }
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE,
                        "Failed to load glowing data for " + playerId, e);
            }
        });
    }

    // ── Respawn handling ──────────────────────────────────────────────────────

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (isGlowEnabled(player.getUniqueId())) {
            // Delay one tick so the player is fully respawned before we apply effects
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    applyGlow(player);
                }
            }, 1L);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Returns the DyeColor values in GUI display order. */
    public static DyeColor[] getColorOrder() {
        return COLOR_ORDER;
    }

    private void saveToDatabase(UUID playerId, boolean enabled, String color) {
        plugin.getAsyncTaskManager().runAsync(() -> {
            String sql = upsertSql();
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerId.toString());
                stmt.setInt(2, enabled ? 1 : 0);
                stmt.setString(3, color);
                if (plugin.getDatabaseManager().getDatabaseType() == DatabaseType.MYSQL) {
                    stmt.setInt(4, enabled ? 1 : 0);
                    stmt.setString(5, color);
                }
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE,
                        "Failed to save glowing data for " + playerId, e);
            }
        });
    }

    private String upsertSql() {
        if (plugin.getDatabaseManager().getDatabaseType() == DatabaseType.MYSQL) {
            return "INSERT INTO player_glowing (player_uuid, enabled, color) VALUES (?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE enabled = ?, color = ?";
        }
        return "INSERT OR REPLACE INTO player_glowing (player_uuid, enabled, color) VALUES (?, ?, ?)";
    }

    @SuppressWarnings("deprecation")
    private ChatColor dyeColorToChatColor(DyeColor dyeColor) {
        return switch (dyeColor) {
            case WHITE -> ChatColor.WHITE;
            case ORANGE -> ChatColor.GOLD;
            case MAGENTA -> ChatColor.LIGHT_PURPLE;
            case LIGHT_BLUE -> ChatColor.AQUA;
            case YELLOW -> ChatColor.YELLOW;
            case LIME -> ChatColor.GREEN;
            case PINK -> ChatColor.LIGHT_PURPLE;
            case GRAY -> ChatColor.DARK_GRAY;
            case LIGHT_GRAY -> ChatColor.GRAY;
            case CYAN -> ChatColor.DARK_AQUA;
            case PURPLE -> ChatColor.DARK_PURPLE;
            case BLUE -> ChatColor.BLUE;
            case BROWN -> ChatColor.GOLD;
            case GREEN -> ChatColor.DARK_GREEN;
            case RED -> ChatColor.RED;
            case BLACK -> ChatColor.BLACK;
        };
    }
}
