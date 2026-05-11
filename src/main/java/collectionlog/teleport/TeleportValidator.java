package collectionlog.teleport;

import collectionlog.CollectionLogPlugin;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;

public class TeleportValidator {
    private final CollectionLogPlugin plugin;

    public TeleportValidator(CollectionLogPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isBannedWorld(String worldName) {
        List<String> banned = plugin.getConfig().getStringList("teleport.banned_worlds");
        return banned.contains(worldName);
    }

    public boolean hasRequiredCollection(Player player) {
        String required = plugin.getConfig().getString("teleport.required_collection", "");
        if (required == null || required.isEmpty()) return true;
        return plugin.getMilestoneManager().hasCompletedMilestone(player.getUniqueId(), required);
    }

    public boolean hasSufficientXP(Player player, int cost) {
        return player.getLevel() >= cost;
    }

    public boolean isCrossWorld(Location from, Location to) {
        if (from.getWorld() == null || to.getWorld() == null) return false;
        return !from.getWorld().getName().equals(to.getWorld().getName());
    }

    public boolean isCrossDimension(Location from, Location to) {
        if (from.getWorld() == null || to.getWorld() == null) return false;
        World.Environment fromEnv = from.getWorld().getEnvironment();
        World.Environment toEnv = to.getWorld().getEnvironment();
        return fromEnv != toEnv;
    }
}
