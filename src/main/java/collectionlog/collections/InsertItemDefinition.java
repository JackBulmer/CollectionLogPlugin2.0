package collectionlog.collections;

import org.bukkit.Material;

/**
 * Represents a custom item to insert into an existing collection.
 */
public class InsertItemDefinition {
    private final String name;
    private final int customModelData;
    private final String subCollection;
    private final Material material;

    public InsertItemDefinition(String name, int customModelData, String subCollection, Material material) {
        this.name = name;
        this.customModelData = customModelData;
        this.subCollection = subCollection;
        this.material = material == null ? Material.PAPER : material;
    }

    public String getName() {
        return name;
    }

    public int getCustomModelData() {
        return customModelData;
    }

    public String getSubCollection() {
        return subCollection;
    }

    public Material getMaterial() {
        return material;
    }

    public String getSignature() {
        return name + ":" + customModelData;
    }

    public boolean hasSubCollection() {
        return subCollection != null && !subCollection.isBlank();
    }
}
