package collectionlog.perks;

import collectionlog.CollectionLogPlugin;
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

    private static final String MODIFIER_NAME = "collectionlog.extra_hearts";
    /** Maximum number of extra hearts (4 hearts = 8 HP). */
    private static final int MAX_EXTRA_HEARTS = 4;
    /** Health points added per extra heart (1 heart = 2 HP). */
    private static final double HEALTH_PER_HEART = 2.0;
    /** Maximum total health bonus from extra hearts. */
    private static final double MAX_EXTRA_HEART_HEALTH = MAX_EXTRA_HEARTS * HEALTH_PER_HEART;

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

        plugin.getLogger().info("[DEBUG] applyExtraHearts called for " + player.getName() + ", count: " + extraHeartCount);

        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (attribute == null) {
            plugin.getLogger().warning("[DEBUG] MAX_HEALTH attribute is null for " + player.getName());
            return;
        }

        plugin.getLogger().info("[DEBUG] Current max health: " + attribute.getValue());
        plugin.getLogger().info("[DEBUG] Current modifiers: " + attribute.getModifiers().size());

        // Log all modifier details to diagnose the issue
        for (AttributeModifier mod : attribute.getModifiers()) {
            plugin.getLogger().info("[DEBUG] Found modifier - Name: '" + mod.getName() + "', Amount: " + mod.getAmount() + ", Operation: " + mod.getOperation());
        }

        // Remove existing modifier if present - try multiple matching strategies
        long removedCount = attribute.getModifiers().stream()
                .filter(mod -> {
                    // Match by name (case-insensitive for safety)
                    boolean nameMatch = MODIFIER_NAME.equalsIgnoreCase(mod.getName());
                    // Fallback: match ADD_NUMBER modifiers with an even amount in [2, 8]
                    // (1-4 extra hearts = 2-8 HP) whose name contains "heart"
                    boolean amountMatch = mod.getOperation() == AttributeModifier.Operation.ADD_NUMBER
                        && mod.getAmount() > 0
                        && mod.getAmount() <= MAX_EXTRA_HEART_HEALTH
                        && mod.getAmount() % HEALTH_PER_HEART == 0;
                    boolean isFallbackMatch = amountMatch
                        && mod.getName() != null
                        && mod.getName().toLowerCase().contains("heart");

                    boolean shouldRemove = nameMatch || isFallbackMatch;
                    if (shouldRemove) {
                        plugin.getLogger().info("[DEBUG] Removing modifier: " + mod.getName() + " with value: " + mod.getAmount());
                    }
                    return shouldRemove;
                })
                .peek(attribute::removeModifier)
                .count();

        plugin.getLogger().info("[DEBUG] Removed " + removedCount + " modifiers");

        // Add new modifier if player has extra hearts
        if (extraHeartCount > 0) {
            double healthToAdd = extraHeartCount * HEALTH_PER_HEART;
            plugin.getLogger().info("[DEBUG] Adding modifier with value: " + healthToAdd);
            AttributeModifier modifier = new AttributeModifier(
                    MODIFIER_NAME,
                    healthToAdd,
                    AttributeModifier.Operation.ADD_NUMBER
            );
            attribute.addModifier(modifier);
            plugin.getLogger().info("[DEBUG] Modifier added successfully");
        } else {
            plugin.getLogger().info("[DEBUG] No extra hearts to add");
        }

        plugin.getLogger().info("[DEBUG] New max health: " + attribute.getValue());
        plugin.getLogger().info("[DEBUG] Current health: " + player.getHealth());

        // Adjust health downward if now over max
        if (player.getHealth() > attribute.getValue()) {
            plugin.getLogger().info("[DEBUG] Adjusting health down from " + player.getHealth() + " to " + attribute.getValue());
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
                .filter(mod -> {
                    boolean nameMatch = MODIFIER_NAME.equalsIgnoreCase(mod.getName());
                    boolean amountMatch = mod.getOperation() == AttributeModifier.Operation.ADD_NUMBER
                        && mod.getAmount() > 0
                        && mod.getAmount() <= MAX_EXTRA_HEART_HEALTH
                        && mod.getAmount() % HEALTH_PER_HEART == 0;
                    boolean isFallbackMatch = amountMatch
                        && mod.getName() != null
                        && mod.getName().toLowerCase().contains("heart");
                    return nameMatch || isFallbackMatch;
                })
                .forEach(attribute::removeModifier);

        // Adjust health if now over max
        if (player.getHealth() > attribute.getValue()) {
            player.setHealth(attribute.getValue());
        }
    }
}
