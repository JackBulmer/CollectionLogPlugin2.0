package collectionlog.collections;

import java.util.List;

/**
 * Represents a file that inserts items into an existing collection.
 */
public class InsertCollectionDefinition {
    private final String collectionId;
    private final List<InsertItemDefinition> items;

    public InsertCollectionDefinition(String collectionId, List<InsertItemDefinition> items) {
        this.collectionId = collectionId;
        this.items = items != null ? items : List.of();
    }

    public String getCollectionId() {
        return collectionId;
    }

    public List<InsertItemDefinition> getItems() {
        return items;
    }
}
