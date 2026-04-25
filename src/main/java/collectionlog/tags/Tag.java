package collectionlog.tags;

/**
 * Represents a player tag that can be equipped as a prefix or suffix.
 */
public class Tag {
    private final String name;
    private final boolean isPrefix;
    private final String requiredCollection; // null if no requirement

    public Tag(String name, boolean isPrefix, String requiredCollection) {
        this.name = name;
        this.isPrefix = isPrefix;
        this.requiredCollection = requiredCollection;
    }

    public String getName() {
        return name;
    }

    public boolean isPrefix() {
        return isPrefix;
    }

    public String getRequiredCollection() {
        return requiredCollection;
    }

    public boolean hasRequirement() {
        return requiredCollection != null;
    }
}
