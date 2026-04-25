package collectionlog.collections;

/**
 * Enum for special collection types (items without unique Material IDs)
 */
public enum SpecialCollectionType {
    ENCHANTMENTS("enchantments", "Enchantments"),
    POTIONS("potions", "Potions"),
    ARMOUR_TRIMS("armour_trims", "Armour Trims"),
    HORNS("horns", "Horns"),
    MOB_HEADS("mob_heads", "Mob Heads"),
    SPAWN_EGGS("spawn_eggs", "Spawn Eggs"),
    JB_FISHING_PLUS("jb_fishing_plus", "JB Fishing +"),
    TCG("tcg", "TCG");

    private final String jsonFile;
    private final String displayName;

    SpecialCollectionType(String jsonFile, String displayName) {
        this.jsonFile = jsonFile;
        this.displayName = displayName;
    }

    public String getJsonFile() {
        return jsonFile;
    }

    public String getDisplayName() {
        return displayName;
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
