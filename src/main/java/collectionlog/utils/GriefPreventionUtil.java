package collectionlog.utils;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Utility class for GriefPrevention integration.
 * Provides methods to check if a player can build/break blocks in claims.
 */
public final class GriefPreventionUtil {

    private static Plugin griefPreventionPlugin = null;
    private static boolean griefPreventionEnabled = false;

    private GriefPreventionUtil() {
    }

    /**
     * Initialise GriefPrevention integration.
     * Call this during plugin startup.
     */
    public static void initialize(Plugin plugin) {
        griefPreventionPlugin = plugin.getServer().getPluginManager().getPlugin("GriefPrevention");
        griefPreventionEnabled = griefPreventionPlugin != null && griefPreventionPlugin.isEnabled();

        if (griefPreventionEnabled) {
            plugin.getLogger().info("GriefPrevention integration enabled");
        } else {
            plugin.getLogger().info("GriefPrevention not found - skipping claim protection checks");
        }
    }

    /**
     * Check if GriefPrevention is available and enabled.
     */
    public static boolean isEnabled() {
        return griefPreventionEnabled;
    }

    /**
     * Check if a player can break a block at the given location.
     *
     * @param player   the player attempting to break the block
     * @param location the location of the block
     * @param material the material of the block
     * @return {@code true} if the player can break, {@code false} if denied by a claim
     */
    public static boolean canBreak(Player player, Location location, Material material) {
        if (!griefPreventionEnabled) {
            return true; // No GriefPrevention – allow by default
        }

        try {
            Claim claim = GriefPrevention.instance.dataStore.getClaimAt(location, false, null);
            if (claim == null) {
                return true; // No claim at location
            }
            // null denial = allowed; non-null = denied
            return claim.allowBreak(player, material) == null;
        } catch (Exception e) {
            // If the GP API fails, fail-open so the perk still works
            return true;
        }
    }

    /**
     * Check if a player can place a block at the given location.
     *
     * @param player   the player attempting to place the block
     * @param location the location of the block
     * @param material the material being placed
     * @return {@code true} if the player can build, {@code false} if denied by a claim
     */
    public static boolean canBuild(Player player, Location location, Material material) {
        if (!griefPreventionEnabled) {
            return true; // No GriefPrevention – allow by default
        }

        try {
            Claim claim = GriefPrevention.instance.dataStore.getClaimAt(location, false, null);
            if (claim == null) {
                return true; // No claim at location
            }
            return claim.allowBuild(player, material) == null;
        } catch (Exception e) {
            return true;
        }
    }
}
