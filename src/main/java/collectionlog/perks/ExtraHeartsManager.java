package collectionlog.perks;

import collectionlog.CollectionLogPlugin;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Manages the extra hearts system for players who complete collections.
 * Each "extra_heart" perk milestone adds 1 health point (0.5 hearts) to the player's max health.
 * Up to 5 collections can award this perk, for a maximum of +2.5 hearts (5 health points).
 */
public class ExtraHeartsManager {

    private static final String MODIFIER_NAME = "collectionlog.extra_hearts";
    private static final UUID MODIFIER_UUID = UUID.fromString("a3d7c2f1-84e6-4b5a-9c08-f1e2d3a4b5c6");

    private final CollectionLogPlugin plugin;

    public ExtraHeartsManager(CollectionLogPlugin plugin) {
        this.plugin = plugin;
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
                .filter(mod -> MODIFIER_UUID.equals(mod.getUniqueId()))
                .forEach(attribute::removeModifier);

        // Add new modifier if player has extra hearts
        if (extraHeartCount > 0) {
            AttributeModifier modifier = new AttributeModifier(
                    MODIFIER_UUID,
                    MODIFIER_NAME,
                    extraHeartCount, // Each extra_heart milestone = +1 health point (0.5 hearts)
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
                .filter(mod -> MODIFIER_UUID.equals(mod.getUniqueId()))
                .forEach(attribute::removeModifier);

        // Adjust health if now over max
        if (player.getHealth() > attribute.getValue()) {
            player.setHealth(attribute.getValue());
        }
    }
}
