package collectionlog.collections;

/**
 * Enum for special collection types (items without unique Material IDs)
 */
public enum SpecialCollectionType {
    ENCHANTMENTS("enchantments", "Enchantments", false),
    POTIONS("potions", "Potions", false),
    ARMOUR_TRIMS("armour_trims", "Armour Trims", false),
    HORNS("horns", "Horns", false),
    MOB_HEADS("mob_heads", "Mob Heads", false),
    SPAWN_EGGS("spawn_eggs", "Spawn Eggs", false),
    JB_FISHING_PLUS("jb_fishing_plus", "JB Fishing +", false),
    TCG("tcg", "TCG", false),
    WITHER_OF_LOST_SOULS("wither_of_lost_souls", "Wither of Lost Souls", true),
    ANCIENT_WARDEN("ancient_warden", "Ancient Warden", true),
    ELDER_DRAGON("elder_dragon", "Elder Dragon", true);

    private final String jsonFile;
    private final String displayName;
    private final boolean perItemMaterial;

    SpecialCollectionType(String jsonFile, String displayName, boolean perItemMaterial) {
        this.jsonFile = jsonFile;
        this.displayName = displayName;
        this.perItemMaterial = perItemMaterial;
    }

    public String getJsonFile() {
        return jsonFile;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isPerItemMaterial() {
        return perItemMaterial;
    }

    public static SpecialCollectionType fromId(String id) {
        for (SpecialCollectionType type : values()) {
            if (type.jsonFile.equalsIgnoreCase(id)) {
                return type;
            }
        }
        return null;
    }
}
