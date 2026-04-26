package collectionlog.perks;

import collectionlog.CollectionLogPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;

/**
 * Applies and removes permanent potion effects that correspond to potion-effect perks.
 *
 * <p>Passive effects (fire resistance, dolphin's grace, water breathing, night vision,
 * slow falling) are applied whenever the perk is unlocked and enabled.
 * Bad Omen IV is toggleable and additionally auto-disables when Bad Omen is
 * converted to Raid Omen or Trial Omen upon entering a village or trial chamber.
 */
public class PotionEffectManager implements Listener {

    /**
     * Duration used for all perk-applied effects.
     * Using {@link Integer#MAX_VALUE} makes effects effectively infinite and also
     * serves as a marker that lets us identify effects applied by this system.
     */
    private static final int INFINITE = Integer.MAX_VALUE;

    /** Maps perk ID → the potion effect type it applies (excludes bad omen). */
    private static final Map<String, PotionEffectType> EFFECT_PERKS = Map.of(
            "permanent_fire_resistance", PotionEffectType.FIRE_RESISTANCE,
            "permanent_dolphins_grace",  PotionEffectType.DOLPHINS_GRACE,
            "permanent_water_breathing", PotionEffectType.WATER_BREATHING,
            "permanent_night_vision",    PotionEffectType.NIGHT_VISION,
            "permanent_slow_falling",    PotionEffectType.SLOW_FALLING
    );

    private final CollectionLogPlugin plugin;
    private final PerkManager perkManager;

    public PotionEffectManager(CollectionLogPlugin plugin) {
        this.plugin = plugin;
        this.perkManager = plugin.getPerkManager();
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Applies every potion-effect perk that is currently active for the player.
     * Called on player join and whenever a new perk is unlocked mid-session.
     */
    public void applyPermanentEffects(Player player) {
        UUID playerId = player.getUniqueId();

        for (Map.Entry<String, PotionEffectType> entry : EFFECT_PERKS.entrySet()) {
            if (perkManager.isActive(playerId, entry.getKey())) {
                applyEffect(player, entry.getValue(), 0, false);
            }
        }

        if (perkManager.isActive(playerId, "permanent_bad_omen")) {
            applyBadOmen(player);
        }
    }

    /**
     * Applies or removes a single potion-effect perk's effect in response to
     * a player toggling the perk on or off in the GUI.
     *
     * @param player  the player who toggled the perk
     * @param perkId  the ID of the perk that was toggled
     * @param enabled whether the perk was just enabled ({@code true}) or disabled ({@code false})
     */
    public void onPerkToggled(Player player, String perkId, boolean enabled) {
        if ("permanent_bad_omen".equals(perkId)) {
            if (enabled) {
                applyBadOmen(player);
            } else {
                removeOurEffect(player, PotionEffectType.BAD_OMEN);
            }
            return;
        }

        PotionEffectType type = EFFECT_PERKS.get(perkId);
        if (type == null) {
            return;
        }

        if (enabled) {
            applyEffect(player, type, 0, false);
        } else {
            removeOurEffect(player, type);
        }
    }

    // ─── Event handler ────────────────────────────────────────────────────────

    /**
     * Detects when Bad Omen is converted to Raid Omen or Trial Omen and
     * automatically disables the {@code permanent_bad_omen} perk toggle so
     * the effect is not immediately reapplied.
     */
    @EventHandler(ignoreCancelled = true)
    public void onPotionEffectChange(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getNewEffect() == null) return;

        PotionEffectType newType = event.getNewEffect().getType();
        if (newType != PotionEffectType.RAID_OMEN && newType != PotionEffectType.TRIAL_OMEN) return;

        UUID playerId = player.getUniqueId();
        if (!perkManager.hasUnlocked(playerId, "permanent_bad_omen")) return;
        if (!perkManager.isEnabled(playerId, "permanent_bad_omen")) return;

        perkManager.setEnabled(playerId, "permanent_bad_omen", false);
        String converted = (newType == PotionEffectType.RAID_OMEN) ? "Raid Omen" : "Trial Omen";
        player.sendMessage("§e⚠ Bad Omen perk disabled (converted to " + converted + ")");
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /**
     * Applies a potion effect with {@link #INFINITE} duration to the player.
     *
     * @param player    the target player
     * @param type      the effect type to apply
     * @param amplifier the amplifier (0 = level I)
     * @param showIcon  whether to show the effect icon in the player's HUD
     */
    private void applyEffect(Player player, PotionEffectType type, int amplifier, boolean showIcon) {
        player.addPotionEffect(new PotionEffect(
                type,
                INFINITE,
                amplifier,
                true,      // ambient – reduces particle density
                false,     // no particles
                showIcon
        ));
    }

    /** Applies Bad Omen IV (amplifier 3) with an HUD icon to the player. */
    private void applyBadOmen(Player player) {
        applyEffect(player, PotionEffectType.BAD_OMEN, 3, true);
    }

    /**
     * Removes the given effect from the player only if it was applied by this
     * system (identified by its {@link #INFINITE} duration).  Player-obtained
     * effects with finite durations are left untouched.
     */
    private void removeOurEffect(Player player, PotionEffectType type) {
        PotionEffect current = player.getPotionEffect(type);
        if (current != null && current.getDuration() == INFINITE) {
            player.removePotionEffect(type);
        }
    }
}
