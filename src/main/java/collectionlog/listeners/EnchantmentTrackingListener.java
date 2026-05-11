package collectionlog.listeners;

import collectionlog.CollectionLogPlugin;
import collectionlog.anticheat.ItemTrackingManager;
import collectionlog.managers.ProgressTrackingManager;
import collectionlog.utils.CollectionMatcher;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks enchantment-collection progress from non-book enchanted items and anvil upgrades.
 */
public class EnchantmentTrackingListener implements Listener {
    private final CollectionLogPlugin plugin;
    private final CollectionMatcher collectionMatcher;
    private final ProgressTrackingManager progressTrackingManager;
    private final ItemTrackingManager itemTrackingManager;
    private final NamespacedKey itemKey;

    public EnchantmentTrackingListener(CollectionLogPlugin plugin) {
        this.plugin = plugin;
        this.collectionMatcher = plugin.getCollectionMatcher();
        this.progressTrackingManager = plugin.getProgressTrackingManager();
        this.itemTrackingManager = plugin.getItemTrackingManager();
        this.itemKey = new NamespacedKey(plugin, "player_placed_item");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || collectionMatcher == null
                || progressTrackingManager == null
                || itemTrackingManager == null) {
            return;
        }

        UUID dropper = itemTrackingManager.getDropper(event.getItem());
        if (dropper != null && !dropper.equals(player.getUniqueId())) {
            return;
        }

        ItemStack item = event.getItem().getItemStack();
        if (item.getType() == Material.ENCHANTED_BOOK || isItemPlayerPlaced(item)) {
            return;
        }

        trackEnchantments(player, item, false, "item pickup");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVillagerTrade(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || !(event.getInventory() instanceof MerchantInventory)
                || event.getRawSlot() != 2
                || !isTakeAction(event.getAction())
                || collectionMatcher == null
                || progressTrackingManager == null) {
            return;
        }

        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType().isAir() || result.getType() == Material.ENCHANTED_BOOK) {
            return;
        }

        trackEnchantments(player, result, false, "villager trade");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH
                || !(event.getCaught() instanceof Item caughtItem)
                || collectionMatcher == null
                || progressTrackingManager == null) {
            return;
        }

        ItemStack item = caughtItem.getItemStack();
        if (item.getType() == Material.ENCHANTED_BOOK) {
            return;
        }

        trackEnchantments(event.getPlayer(), item, false, "fishing");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAnvilResultTake(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getView().getTopInventory().getType() != InventoryType.ANVIL
                || event.getSlotType() != InventoryType.SlotType.RESULT
                || !isTakeAction(event.getAction())
                || collectionMatcher == null
                || progressTrackingManager == null) {
            return;
        }

        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType().isAir()) {
            return;
        }

        trackEnchantments(player, result, true, "anvil");
    }

    private void trackEnchantments(Player player, ItemStack item, boolean includeStoredEnchantments, String source) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        Map<Enchantment, Integer> enchantments = new LinkedHashMap<>(item.getEnchantments());
        if (includeStoredEnchantments && meta instanceof EnchantmentStorageMeta storageMeta) {
            storageMeta.getStoredEnchants().forEach((enchantment, level) ->
                    enchantments.merge(enchantment, Math.max(1, level), Math::max));
        }
        if (enchantments.isEmpty()) {
            return;
        }

        for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
            String enchantmentKey = entry.getKey().getKey().getKey();
            int level = Math.max(1, entry.getValue());

            for (CollectionMatcher.CollectionMatch match : collectionMatcher.findMatchesForEnchantment(enchantmentKey, level)) {
                progressTrackingManager.trackItemCollection(player, match.collection(), match.signature())
                        .exceptionally(throwable -> {
                            plugin.getLogger().severe(
                                    "Failed to track " + source + " enchantment progress: " + throwable.getMessage());
                            return null;
                        });
            }
        }
    }

    private boolean isItemPlayerPlaced(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE);
    }

    private boolean isTakeAction(InventoryAction action) {
        return switch (action) {
            case PICKUP_ALL,
                 PICKUP_HALF,
                 PICKUP_ONE,
                 PICKUP_SOME,
                 MOVE_TO_OTHER_INVENTORY,
                 HOTBAR_MOVE_AND_READD,
                 HOTBAR_SWAP,
                 COLLECT_TO_CURSOR -> true;
            default -> false;
        };
    }
}
