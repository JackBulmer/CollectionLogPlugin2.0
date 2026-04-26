package collectionlog.perks;

import org.bukkit.Material;

import java.util.List;

/**
 * Represents a perk that can be unlocked by completing a collection milestone.
 * Perks may be toggleable (the player can enable/disable them) and may be
 * categorised as potion effects (displayed on the Potion Effects sub-page of
 * the /perks GUI).
 */
public class Perk {
    private final String id;
    private final String name;
    private final List<String> description;
    private final Material icon;
    /** Collection (or sub-collection) ID required to unlock this perk, or null if none. */
    private final String requiredCollection;
    private final boolean toggleable;
    private final boolean potionEffect;
    /** Whether this perk should appear in the /perks GUI. Automatic/passive perks are excluded. */
    private final boolean showInGui;

    public Perk(String id, String name, List<String> description, Material icon,
                String requiredCollection, boolean toggleable, boolean potionEffect,
                boolean showInGui) {
        this.id = id;
        this.name = name;
        this.description = description != null ? List.copyOf(description) : List.of();
        this.icon = icon;
        this.requiredCollection = requiredCollection;
        this.toggleable = toggleable;
        this.potionEffect = potionEffect;
        this.showInGui = showInGui;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<String> getDescription() {
        return description;
    }

    public Material getIcon() {
        return icon;
    }

    /** The collection ID required to unlock this perk, or null if always unlocked. */
    public String getRequiredCollection() {
        return requiredCollection;
    }

    /** Whether the player can toggle this perk on/off after unlocking it. */
    public boolean isToggleable() {
        return toggleable;
    }

    /** Whether this perk applies a persistent potion effect. */
    public boolean isPotionEffect() {
        return potionEffect;
    }

    /** Whether this perk should be shown in the /perks GUI. */
    public boolean isShowInGui() {
        return showInGui;
    }
}
