package collectionlog.models;

import collectionlog.collections.CollectionDefinition;
import collectionlog.collections.SubCollectionDefinition;

/**
 * Result of tracking an item collection
 */
public class ProgressResult {
    private final boolean isNewItem;
    private final boolean completedCollection;
    private final boolean completedSubCollection;
    private final CollectionDefinition collection;
    private final SubCollectionDefinition subCollection;
    private final PlayerCollectionProgress progress;

    public ProgressResult(boolean isNewItem, boolean completedCollection, boolean completedSubCollection,
                          CollectionDefinition collection, SubCollectionDefinition subCollection,
                          PlayerCollectionProgress progress) {
        this.isNewItem = isNewItem;
        this.completedCollection = completedCollection;
        this.completedSubCollection = completedSubCollection;
        this.collection = collection;
        this.subCollection = subCollection;
        this.progress = progress;
    }

    public static ProgressResult noProgress() {
        return new ProgressResult(false, false, false, null, null, null);
    }

    public boolean isNewItem() {
        return isNewItem;
    }

    public boolean completedCollection() {
        return completedCollection;
    }

    public boolean completedSubCollection() {
        return completedSubCollection;
    }

    public CollectionDefinition getCollection() {
        return collection;
    }

    public SubCollectionDefinition getSubCollection() {
        return subCollection;
    }

    public PlayerCollectionProgress getProgress() {
        return progress;
    }

    public boolean hasProgress() {
        return isNewItem;
    }
}
