package collectionlog.collections;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents rewards for completing a collection or sub-collection
 */
public class CollectionReward {
    private final int xp;
    private final List<String> items;
    private final List<String> perks;
    private final List<String> tags;

    public CollectionReward(int xp, List<String> items, List<String> perks, List<String> tags) {
        this.xp = xp;
        this.items = items != null ? items : new ArrayList<>();
        this.perks = perks != null ? perks : new ArrayList<>();
        this.tags = tags != null ? tags : new ArrayList<>();
    }

    public int getXp() {
        return xp;
    }

    public List<String> getItems() {
        return items;
    }

    public List<String> getPerks() {
        return perks;
    }

    public List<String> getTags() {
        return tags;
    }

    public boolean hasRewards() {
        return xp > 0 || !items.isEmpty() || !perks.isEmpty() || !tags.isEmpty();
    }

    @Override
    public String toString() {
        return "Reward{" +
                "xp=" + xp +
                ", items=" + items.size() +
                ", perks=" + perks.size() +
                ", tags=" + tags.size() +
                '}';
    }
}
