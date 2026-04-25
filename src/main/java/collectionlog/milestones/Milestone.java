package collectionlog.milestones;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

public class Milestone {
    private final String name;
    private final MilestoneType type;
    private final List<String> perks;
    private final List<String> prefixTags;
    private final List<String> suffixTags;
    private final String toastMessage;
    private final String announcementMessage;

    public Milestone(String name, MilestoneType type, List<String> perks,
                     List<String> prefixTags, List<String> suffixTags,
                     String toastMessage, String announcementMessage) {
        this.name = name;
        this.type = type;
        this.perks = perks == null ? List.of() : new ArrayList<>(perks);
        this.prefixTags = prefixTags == null ? List.of() : new ArrayList<>(prefixTags);
        this.suffixTags = suffixTags == null ? List.of() : new ArrayList<>(suffixTags);
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
        return Collections.unmodifiableList(perks);
    }

    public List<String> getPrefixTags() {
        return Collections.unmodifiableList(prefixTags);
    }

    public List<String> getSuffixTags() {
        return Collections.unmodifiableList(suffixTags);
    }

    public String getToastMessage() {
        return toastMessage;
    }

    public String getAnnouncementMessage() {
        return announcementMessage;
    }
}
