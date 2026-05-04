package collectionlog.perks;

import collectionlog.CollectionLogPlugin;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Manages the extra hearts system for players who complete collections.
 * Each "extra_heart" perk adds 2 health points (1 full heart) to the player's max health.
 * Maximum: 4 extra hearts (8 health points).
 */
public class ExtraHeartsManager {

    private static final String MODIFIER_NAME = "collectionlog.extra_hearts";
    /** Fixed UUID for the extra hearts modifier so it can be reliably found and replaced. */
    private static final UUID MODIFIER_UUID = UUID.fromString("a3f9c1e2-7b4d-4f8a-b5e6-2d1c9f0a3e7b");
    /** Maximum number of extra hearts (4 hearts = 8 HP). */
    private static final int MAX_EXTRA_HEARTS = 4;
    /** Health points added per extra heart (1 heart = 2 HP). */
    private static final double HEALTH_PER_HEART = 2.0;
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    /**
     * Returns true if the modifier is one of our extra heart modifiers, including legacy ones
     * created by the deprecated {@code AttributeModifier(UUID, String, double, Operation)}
     * constructor (which stores the UUID string as the modifier name).
     */
    private static boolean isExtraHeartModifier(AttributeModifier mod) {
        // Check by fixed UUID (for modifiers created after this fix)
        if (MODIFIER_UUID.equals(mod.getUniqueId())) {
            return true;
        }
        if (MODIFIER_NAME.equalsIgnoreCase(mod.getName())) {
            return true;
        }
        // Legacy modifiers: ADD_NUMBER, exactly 2.0 HP, and the name is a bare UUID string
        return mod.getOperation() == AttributeModifier.Operation.ADD_NUMBER
                && Math.abs(mod.getAmount() - HEALTH_PER_HEART) < 0.001
                && mod.getName() != null
                && UUID_PATTERN.matcher(mod.getName()).matches();
    }

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
        int extraHeartCount = Math.min(getExtraHeartCount(playerId), MAX_EXTRA_HEARTS);

        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (attribute == null) {
            return;
        }

        // Remove ALL extra heart modifiers: properly-named ones and legacy UUID-named ones
        List<AttributeModifier> toRemove = new ArrayList<>();
        for (AttributeModifier mod : attribute.getModifiers()) {
            if (isExtraHeartModifier(mod)) {
                toRemove.add(mod);
            }
        }

        for (AttributeModifier mod : toRemove) {
            attribute.removeModifier(mod);
        }

        // Add new modifier if player has extra hearts
        if (extraHeartCount > 0) {
            double healthToAdd = extraHeartCount * HEALTH_PER_HEART;
            @SuppressWarnings("deprecation")
            AttributeModifier modifier = new AttributeModifier(
                    MODIFIER_UUID,
                    MODIFIER_NAME,
                    healthToAdd,
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

        // Remove all extra heart modifiers: properly-named ones and legacy UUID-named ones
        List<AttributeModifier> toRemove = new ArrayList<>();
        for (AttributeModifier mod : attribute.getModifiers()) {
            if (isExtraHeartModifier(mod)) {
                toRemove.add(mod);
            }
        }

        for (AttributeModifier mod : toRemove) {
            attribute.removeModifier(mod);
        }

        // Adjust health if now over max
        if (player.getHealth() > attribute.getValue()) {
            player.setHealth(attribute.getValue());
        }
    }
}
