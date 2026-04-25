package collectionlog.utils;

import collectionlog.collections.CollectionDefinition;
import collectionlog.collections.CollectionLoader;
import collectionlog.collections.SpecialCollectionLoader;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves which collection entries an item belongs to.
 */
public class CollectionMatcher {
    private final ItemSignatureHelper itemSignatureHelper;
    private final Map<String, List<CollectionDefinition>> collectionsBySignature;
    private final CollectionDefinition mobHeadsCollection;
    private final Map<String, String> mobHeadSignaturesByEntity;

    public CollectionMatcher(CollectionLoader collectionLoader, SpecialCollectionLoader specialCollectionLoader) {
        this.itemSignatureHelper = new ItemSignatureHelper();
        this.collectionsBySignature = new LinkedHashMap<>();

        if (collectionLoader != null) {
            collectionLoader.getCollections().values().forEach(this::indexCollection);
        }
        if (specialCollectionLoader != null) {
            specialCollectionLoader.getSpecialCollections().values().forEach(this::indexCollection);
        }

        this.mobHeadsCollection = specialCollectionLoader != null
                ? specialCollectionLoader.getSpecialCollection("mob_heads")
                : null;
        this.mobHeadSignaturesByEntity = new LinkedHashMap<>();
        indexMobHeadsCollection();
    }

    /**
     * Find matching collections for an item.
     */
    public List<CollectionMatch> findMatches(ItemStack item) {
        return findMatches(item, null);
    }

    /**
     * Find matching collections for an item, with optional source entity context.
     */
    public List<CollectionMatch> findMatches(ItemStack item, EntityType sourceEntityType) {
        List<CollectionMatch> matches = new ArrayList<>();
        if (item == null || item.getType().isAir()) {
            return matches;
        }

        Set<String> signatures = itemSignatureHelper.generateSignatures(item, sourceEntityType);
        Set<String> seen = new LinkedHashSet<>();
        for (String signature : signatures) {
            List<CollectionDefinition> collections = collectionsBySignature.get(signature);
            if (collections == null || collections.isEmpty()) {
                continue;
            }

            for (CollectionDefinition collection : collections) {
                String key = collection.getId() + "|" + signature;
                if (seen.add(key)) {
                    matches.add(new CollectionMatch(collection, signature));
                }
            }
        }

        addEntitySpecificMobHeadMatch(item, sourceEntityType, matches, seen);
        return matches;
    }

    private void addEntitySpecificMobHeadMatch(
            ItemStack item,
            EntityType sourceEntityType,
            List<CollectionMatch> matches,
            Set<String> seen
    ) {
        if (mobHeadsCollection == null
                || sourceEntityType == null
                || item.getType() != Material.PLAYER_HEAD) {
            return;
        }

        String entityName = sourceEntityType.name();
        String configuredHeadSignature = mobHeadSignaturesByEntity.get(entityName);
        if (configuredHeadSignature != null) {
            String key = mobHeadsCollection.getId() + "|" + configuredHeadSignature;
            if (seen.add(key)) {
                matches.add(new CollectionMatch(mobHeadsCollection, configuredHeadSignature));
            }
        }
    }

    private void indexCollection(CollectionDefinition collection) {
        if (collection == null) {
            return;
        }

        for (String item : collection.getItems()) {
            collectionsBySignature
                    .computeIfAbsent(item, k -> new ArrayList<>())
                    .add(collection);
        }
    }

    /**
     * Find collection matches for an enchantment by key and level.
     * Checks both the exact "key:level" signature and just the "key" signature.
     */
    public List<CollectionMatch> findMatchesForEnchantment(String enchantmentKey, int level) {
        List<CollectionMatch> matches = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        String leveledSignature = enchantmentKey + ":" + level;
        addMatchesForSignature(leveledSignature, matches, seen);
        addMatchesForSignature(enchantmentKey, matches, seen);

        return matches;
    }

    private void addMatchesForSignature(String signature, List<CollectionMatch> matches, Set<String> seen) {
        List<CollectionDefinition> collections = collectionsBySignature.get(signature);
        if (collections == null || collections.isEmpty()) {
            return;
        }
        for (CollectionDefinition collection : collections) {
            String key = collection.getId() + "|" + signature;
            if (seen.add(key)) {
                matches.add(new CollectionMatch(collection, signature));
            }
        }
    }

    private void indexMobHeadsCollection() {
        if (mobHeadsCollection == null) {
            return;
        }

        for (String signature : mobHeadsCollection.getItems()) {
            String entityName = signature.contains(":")
                    ? signature.substring(0, signature.indexOf(':'))
                    : signature;
            mobHeadSignaturesByEntity.putIfAbsent(entityName, signature);
        }
    }

    public record CollectionMatch(CollectionDefinition collection, String signature) {
    }
}
