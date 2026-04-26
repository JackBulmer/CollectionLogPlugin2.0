package collectionlog.perks;

import collectionlog.CollectionLogPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Manages the extra hearts system for players who complete collections.
 * Each "extra_heart" perk adds 2 health points (1 full heart) to the player's max health.
 * Maximum: 4 extra hearts (8 health points).
 */
public class ExtraHeartsManager {

    private final CollectionLogPlugin plugin;
    private final NamespacedKey modifierKey;

    public ExtraHeartsManager(CollectionLogPlugin plugin) {
        this.plugin = plugin;
        this.modifierKey = new NamespacedKey(plugin, "extra_hearts");
    }

    /**
     * Apply the player's extra hearts based on how many extra_heart perk milestones they have completed.
     * Called on player join and when a new extra_heart perk is unlocked.
     */
    public void applyExtraHearts(Player player) {
        UUID playerId = player.getUniqueId();
        int extraHeartCount = getExtraHeartCount(playerId);

        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (attribute == null) {
            return;
        }

        // Remove existing modifier if present
        attribute.getModifiers().stream()
                .filter(mod -> mod.getKey().equals(modifierKey))
                .forEach(attribute::removeModifier);

        // Add new modifier if player has extra hearts
        if (extraHeartCount > 0) {
            AttributeModifier modifier = new AttributeModifier(
                    modifierKey,
                    extraHeartCount * 2, // Each extra_heart perk = +2 health points (1 heart)
                    AttributeModifier.Operation.ADD_NUMBER
            );
            attribute.addModifier(modifier);
        }

        // Adjust health downward if now over max
        if (player.getHealth() > attribute.getValue()) {
            player.setHealth(attribute.getValue());
        }
    }

    /**
     * Recalculate and update a player's max health.
     * Used when extra_heart perks are unlocked or milestones revoked.
     */
    public void updateExtraHearts(UUID playerId) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null && player.isOnline()) {
            applyExtraHearts(player);
        }
    }

    /**
     * Get how many extra_heart perk milestones the player has completed.
     * Counts completed milestones that include the extra_heart perk, rather than relying
     * on the perk table (which deduplicates by perk ID).
     */
    public int getExtraHeartCount(UUID playerId) {
        return plugin.getMilestoneManager().countExtraHeartMilestones(playerId);
    }

    /**
     * Remove all extra hearts from a player.
     * Used when all extra_heart milestones are revoked.
     */
    public void removeExtraHearts(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (attribute == null) {
            return;
        }

        attribute.getModifiers().stream()
                .filter(mod -> mod.getKey().equals(modifierKey))
                .forEach(attribute::removeModifier);

        // Adjust health if now over max
        if (player.getHealth() > attribute.getValue()) {
            player.setHealth(attribute.getValue());
        }
    }
}
