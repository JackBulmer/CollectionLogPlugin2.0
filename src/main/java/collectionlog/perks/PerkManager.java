package collectionlog.perks;

import collectionlog.CollectionLogPlugin;
import collectionlog.database.DatabaseType;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages perk unlock and toggle state for players.
 *
 * <p>Unlock state (which perks a player has earned) is stored in the
 * {@code player_perks} table and managed by {@link collectionlog.milestones.MilestoneManager}.
 * Toggle state (whether an unlocked, toggleable perk is currently active) is
 * stored in the separate {@code player_perk_toggles} table and managed here.
 */
public class PerkManager {

    private final CollectionLogPlugin plugin;

    /** Per-player set of perk IDs that have been explicitly disabled. */
    private final Map<UUID, Set<String>> disabledPerks = new ConcurrentHashMap<>();

    /** Players whose toggle state has already been loaded from the database. */
    private final Set<UUID> loadedToggles = ConcurrentHashMap.newKeySet();

    public PerkManager(CollectionLogPlugin plugin) {
        this.plugin = plugin;
    }

    // ─── Unlock state (delegates to MilestoneManager) ────────────────────────

    /** Returns true if the player has the given perk unlocked. */
    public boolean hasUnlocked(UUID playerId, String perkId) {
        Perk perk = PerkRegistry.getPerk(perkId);
        if (perk != null && perk.getRequiredCollection() == null) {
            // No collection requirement – always available to all players
            return true;
        }
        return plugin.getMilestoneManager().hasPerk(playerId, perkId);
    }

    /**
     * Unlocks a perk for a player. Persists to the database and updates the
     * in-memory cache.
     */
    public void unlockPerk(UUID playerId, String perkId) {
        plugin.getMilestoneManager().unlockPerk(playerId, perkId);
        // Ensure toggle state is initialised as enabled
        ensureTogglesLoaded(playerId);
        disabledPerks.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet()).remove(perkId);
    }

    /**
     * Revokes a perk from a player. Removes it from the unlock table and the
     * toggle table, and updates the in-memory cache.
     */
    public void revokePerk(UUID playerId, String perkId) {
        plugin.getMilestoneManager().revokePerk(playerId, perkId);
        // Also remove toggle state
        Set<String> disabled = disabledPerks.get(playerId);
        if (disabled != null) {
            disabled.remove(perkId);
        }
        deleteToggleFromDatabase(playerId, perkId);

        // Update extra hearts if this is an extra_heart perk
        if ("extra_heart".equals(perkId)) {
            plugin.getExtraHeartsManager().updateExtraHearts(playerId);
        }

        if ("larger_ender_chest".equals(perkId) && plugin.getLargerEnderChestManager() != null) {
            plugin.getLargerEnderChestManager().handlePerkRevoked(playerId);
        }
    }

    // ─── Toggle state ─────────────────────────────────────────────────────────

    /**
     * Returns true if the given perk is currently enabled for the player.
     * Perks default to enabled when first unlocked.
     */
    public boolean isEnabled(UUID playerId, String perkId) {
        ensureTogglesLoaded(playerId);
        return !disabledPerks.getOrDefault(playerId, Collections.emptySet()).contains(perkId);
    }

    /**
     * Returns true if the player has the perk unlocked AND it is currently
     * enabled. This is the primary check used by perk listeners.
     */
    public boolean isActive(UUID playerId, String perkId) {
        return hasUnlocked(playerId, perkId) && isEnabled(playerId, perkId);
    }

    /** Convenience overload that accepts a Player. */
    public boolean isActive(Player player, String perkId) {
        return isActive(player.getUniqueId(), perkId);
    }

    /**
     * Toggles a perk between enabled and disabled.
     *
     * @return the new enabled state, or false if the perk cannot be toggled
     *         (not unlocked, or not toggleable).
     */
    public boolean togglePerk(UUID playerId, String perkId) {
        if (!hasUnlocked(playerId, perkId)) {
            return false;
        }
        Perk perk = PerkRegistry.getPerk(perkId);
        if (perk == null || !perk.isToggleable()) {
            return false;
        }
        boolean newState = !isEnabled(playerId, perkId);
        setEnabled(playerId, perkId, newState);
        return newState;
    }

    /**
     * Explicitly sets the enabled state of a perk for a player.
     * Persists the change to the database.
     */
    public void setEnabled(UUID playerId, String perkId, boolean enabled) {
        ensureTogglesLoaded(playerId);
        Set<String> disabled = disabledPerks.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet());
        if (enabled) {
            disabled.remove(perkId);
        } else {
            disabled.add(perkId);
        }
        saveToggleToDatabase(playerId, perkId, enabled);
    }

    // ─── Cache management ────────────────────────────────────────────────────

    /**
     * Eagerly loads the toggle state for a player. Called when the player
     * joins so the cache is warm before any perk checks occur.
     *
     * <p>Loads synchronously on the calling thread to guarantee toggle state
     * is available before {@code applyPermanentEffects} runs on the same tick.
     * The data set is small (one row per disabled perk) so the query is fast.
     * This is preferable to an async load because {@code ensureTogglesLoaded}
     * would otherwise do the same synchronous load on the first GUI open,
     * causing an unpredictable lag spike mid-gameplay instead.
     */
    public void loadPlayerToggles(UUID playerId) {
        if (loadedToggles.contains(playerId)) {
            return;
        }
        loadTogglesFromDatabase(playerId);
    }

    /** Removes all cached data for a player (e.g. on disconnect). */
    public void clearCache(UUID playerId) {
        disabledPerks.remove(playerId);
        loadedToggles.remove(playerId);
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    private void ensureTogglesLoaded(UUID playerId) {
        if (!loadedToggles.contains(playerId)) {
            // Load synchronously if not yet cached (happens on first GUI open).
            loadTogglesFromDatabase(playerId);
        }
    }

    private void loadTogglesFromDatabase(UUID playerId) {
        if (!loadedToggles.add(playerId)) {
            // Another thread already loaded (or is loading) toggles for this player
            return;
        }
        Set<String> disabled = ConcurrentHashMap.newKeySet();
        String sql = "SELECT perk_id FROM player_perk_toggles WHERE player_uuid = ? AND enabled = 0";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    disabled.add(rs.getString("perk_id"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load perk toggles for " + playerId, e);
        }
        disabledPerks.put(playerId, disabled);
        loadedToggles.add(playerId);
    }

    private void saveToggleToDatabase(UUID playerId, String perkId, boolean enabled) {
        plugin.getAsyncTaskManager().runAsync(() -> {
            String sql = upsertSql();
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerId.toString());
                stmt.setString(2, perkId);
                stmt.setInt(3, enabled ? 1 : 0);
                if (plugin.getDatabaseManager().getDatabaseType() == DatabaseType.MYSQL) {
                    stmt.setInt(4, enabled ? 1 : 0);
                }
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE,
                        "Failed to save perk toggle for " + playerId + " / " + perkId, e);
            }
        });
    }

    private void deleteToggleFromDatabase(UUID playerId, String perkId) {
        plugin.getAsyncTaskManager().runAsync(() -> {
            String sql = "DELETE FROM player_perk_toggles WHERE player_uuid = ? AND perk_id = ?";
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerId.toString());
                stmt.setString(2, perkId);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE,
                        "Failed to delete perk toggle for " + playerId + " / " + perkId, e);
            }
        });
    }

    /** Returns a DB-engine-appropriate upsert statement for toggle state. */
    private String upsertSql() {
        if (plugin.getDatabaseManager().getDatabaseType() == DatabaseType.MYSQL) {
            return "INSERT INTO player_perk_toggles (player_uuid, perk_id, enabled) "
                    + "VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE enabled = ?";
        }
        return "INSERT OR REPLACE INTO player_perk_toggles (player_uuid, perk_id, enabled) VALUES (?, ?, ?)";
    }
}
