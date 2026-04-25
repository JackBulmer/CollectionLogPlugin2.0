package collectionlog.milestones;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

public class SubCollectionMilestone extends Milestone {
    private final String parentCollection;
    private final String subCollection;
    private final int xpReward;
    private final List<String> itemRewards;

    public SubCollectionMilestone(String name, String parentCollection, String subCollection,
                                  int xpReward, List<String> itemRewards, String toastMessage) {
        super(name, MilestoneType.SUB_COLLECTION,
                Collections.emptyList(),  // perks
                Collections.emptyList(),  // prefixTags
                Collections.emptyList(),  // suffixTags
                toastMessage,            // toastMessage
                "");
        this.parentCollection = parentCollection;
        this.subCollection = subCollection;
        this.xpReward = xpReward;
        this.itemRewards = itemRewards == null ? List.of() : new ArrayList<>(itemRewards);
    }

    public String getParentCollection() {
        return parentCollection;
    }

    public String getSubCollection() {
        return subCollection;
    }

    public int getXpReward() {
        return xpReward;
    }

    public List<String> getItemRewards() {
        return Collections.unmodifiableList(itemRewards);
    }
}
