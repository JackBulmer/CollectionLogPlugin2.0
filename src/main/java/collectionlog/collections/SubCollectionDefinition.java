package collectionlog.collections;

import java.util.List;

/**
 * Represents a sub-collection within a main collection
 */
public class SubCollectionDefinition {
    private final String id;
    private final String name;
    private final List<String> items;
    private final CollectionReward rewards;

    public SubCollectionDefinition(String id, String name, List<String> items, CollectionReward rewards) {
        this.id = id;
        this.name = name;
        this.items = items;
        this.rewards = rewards;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<String> getItems() {
        return items;
    }

    public int getTotalItems() {
        return items.size();
    }

    public CollectionReward getRewards() {
        return rewards;
    }

    @Override
    public String toString() {
        return "SubCollection{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", items=" + items.size() +
                '}';
    }
}
