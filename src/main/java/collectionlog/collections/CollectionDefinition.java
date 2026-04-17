package collectionlog.collections;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a collection definition loaded from collections.json
 */
public class CollectionDefinition {
    private final String id;
    private final String name;
    private final String category;
    private final List<String> items;
    private final List<SubCollectionDefinition> subCollections;
    private final CollectionReward rewards;
    private final boolean isSpecial;

    public CollectionDefinition(String id, String name, String category,
                                List<String> items, CollectionReward rewards, boolean isSpecial) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.items = items != null ? items : new ArrayList<>();
        this.subCollections = new ArrayList<>();
        this.rewards = rewards;
        this.isSpecial = isSpecial;
    }

    public void addSubCollection(SubCollectionDefinition subCollection) {
        this.subCollections.add(subCollection);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public List<String> getItems() {
        return items;
    }

    public int getTotalItems() {
        return items.size();
    }

    public List<SubCollectionDefinition> getSubCollections() {
        return subCollections;
    }

    public CollectionReward getRewards() {
        return rewards;
    }

    public boolean isSpecial() {
        return isSpecial;
    }

    @Override
    public String toString() {
        return "Collection{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", items=" + items.size() +
                ", subCollections=" + subCollections.size() +
                '}';
    }
}
