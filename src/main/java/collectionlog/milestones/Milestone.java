package collectionlog.milestones;

import java.util.List;

public class Milestone {
    private final String name;
    private final MilestoneType type;
    private final List<String> perks;
    private final List<String> tags;
    private final String toastMessage;
    private final String announcementMessage;

    public Milestone(String name, MilestoneType type, List<String> perks, List<String> tags,
                     String toastMessage, String announcementMessage) {
        this.name = name;
        this.type = type;
        this.perks = perks;
        this.tags = tags;
        this.toastMessage = toastMessage;
        this.announcementMessage = announcementMessage;
    }

    public String getName() {
        return name;
    }

    public MilestoneType getType() {
        return type;
    }

    public List<String> getPerks() {
        return perks;
    }

    public List<String> getTags() {
        return tags;
    }

    public String getToastMessage() {
        return toastMessage;
    }

    public String getAnnouncementMessage() {
        return announcementMessage;
    }
}
