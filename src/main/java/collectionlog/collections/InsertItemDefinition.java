package collectionlog.collections;

/**
 * Represents a custom item to insert into an existing collection.
 */
public class InsertItemDefinition {
    private final String name;
    private final int customModelData;
    private final String subCollection;

    public InsertItemDefinition(String name, int customModelData, String subCollection) {
        this.name = name;
        this.customModelData = customModelData;
        this.subCollection = subCollection;
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

    public String getSignature() {
        return name + ":" + customModelData;
    }

    public boolean hasSubCollection() {
        return subCollection != null && !subCollection.isBlank();
    }
}
