package collectionlog.listeners;

import collectionlog.CollectionLogPlugin;
import collectionlog.anticheat.ItemTrackingManager;
import collectionlog.collections.CollectionDefinition;
import collectionlog.managers.ProgressTrackingManager;
import collectionlog.utils.CollectionMatcher;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * Tracks item pickups to prevent collection credit theft.
 * Players CAN pick up items, but won't get credit if they didn't earn them.
 */
public class ItemPickupListener implements Listener {
    private final CollectionLogPlugin plugin;
    private final ItemTrackingManager itemTrackingManager;
    private final CollectionMatcher collectionMatcher;
    private final ProgressTrackingManager progressTrackingManager;
    private final NamespacedKey mythicMobHeadKey;
    private final NamespacedKey mythicMobHeadIdKey;

    public ItemPickupListener(CollectionLogPlugin plugin) {
        this.plugin = plugin;
        this.itemTrackingManager = plugin.getItemTrackingManager();
        this.collectionMatcher = plugin.getCollectionMatcher();
        this.progressTrackingManager = plugin.getProgressTrackingManager();
        this.mythicMobHeadKey = new NamespacedKey(plugin, "mythic_mob_head");
        this.mythicMobHeadIdKey = new NamespacedKey(plugin, "mythic_mob_head_id");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player picker)) {
            return;
        }

        UUID dropper = itemTrackingManager.getDropper(event.getItem());
        boolean shouldTrackCollectionCredit = false;
        if (dropper == null) {
            shouldTrackCollectionCredit = true;
            if (plugin.getConfig().getBoolean("plugin.debug", false)) {
                plugin.getLogger().info(picker.getName() + " picked up natural item (will get collection credit)");
            }
        } else if (!dropper.equals(picker.getUniqueId())) {
            // Pickup is allowed; only credit is denied in later collection-processing phases.
            if (plugin.getConfig().getBoolean("plugin.debug", false)) {
                plugin.getLogger().info(picker.getName() + " picked up item from " + dropper
                        + " (will not get collection credit)");
            }
        } else {
            shouldTrackCollectionCredit = true;
            if (plugin.getConfig().getBoolean("plugin.debug", false)) {
                plugin.getLogger().info(picker.getName() + " picked up their own item (will get collection credit)");
            }
        }

        if (shouldTrackCollectionCredit) {
            trackCollectionProgress(picker, event.getItem().getItemStack());
        }

        // Clean up tracking immediately after pickup
        itemTrackingManager.untrack(event.getItem());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemDespawn(ItemDespawnEvent event) {
        if (!itemTrackingManager.isTracked(event.getEntity())) {
            return;
        }

        itemTrackingManager.untrack(event.getEntity());

        if (plugin.getConfig().getBoolean("plugin.debug", false)) {
            plugin.getLogger().info("Item despawned, removed from tracking");
        }
    }

    private void trackCollectionProgress(Player player, ItemStack item) {
        if (collectionMatcher == null || progressTrackingManager == null) {
            return;
        }

        if (trackMythicMobHeadProgress(player, item)) {
            return;
        }

        EntityType mobHeadEntityType = extractMobHeadEntityType(item);

        for (CollectionMatcher.CollectionMatch match : collectionMatcher.findMatches(item, mobHeadEntityType)) {
            progressTrackingManager.trackItemCollection(player, match.collection(), match.signature())
                    .exceptionally(throwable -> {
                        plugin.getLogger().severe("Failed to track picked-up item progress: " + throwable.getMessage());
                        return null;
                    });
        }
    }

    /**
     * Extracts the source entity type from a mob head item's persistent data.
     * Returns null if the item is not a tagged mob head.
     */
    private EntityType extractMobHeadEntityType(ItemStack item) {
        if (item.getType() != Material.PLAYER_HEAD) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        NamespacedKey key = new NamespacedKey(plugin, "mob_head");
        String value = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (value == null) {
            return null;
        }
        // Stored format is "ENTITY_NAME_HEAD"; strip the trailing "_HEAD" to get entity name.
        String entityName = value.endsWith("_HEAD") ? value.substring(0, value.length() - 5) : value;
        try {
            return EntityType.valueOf(entityName);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean trackMythicMobHeadProgress(Player player, ItemStack item) {
        if (item.getType() != Material.PLAYER_HEAD) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        PersistentDataContainer dataContainer = meta.getPersistentDataContainer();

        String mythicInternalName = dataContainer.get(mythicMobHeadKey, PersistentDataType.STRING);
        if (mythicInternalName == null || mythicInternalName.isBlank()) {
            return false;
        }

        String headId = dataContainer.get(mythicMobHeadIdKey, PersistentDataType.STRING);
        if (headId == null || headId.isBlank()) {
            plugin.getLogger().warning("Mythic mob head missing HeadDatabase ID tag for mob: " + mythicInternalName);
            return true;
        }

        String signature = "MYTHIC:" + headId;

        // Get the CollectionDefinition object first
        CollectionDefinition mobHeadsCollection = plugin.getCollectionLoader().getCollection("mob_heads");
        if (mobHeadsCollection != null) {
            progressTrackingManager.trackItemCollection(player, mobHeadsCollection, signature)
                    .exceptionally(throwable -> {
                        plugin.getLogger().severe("Failed to track Mythic mob head pickup: " + throwable.getMessage());
                        return null;
                    });
        } else {
            plugin.getLogger().warning("mob_heads collection not found!");
        }

        return true;
    }
}
