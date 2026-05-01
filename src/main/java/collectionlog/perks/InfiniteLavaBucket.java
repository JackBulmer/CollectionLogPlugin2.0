package collectionlog.perks;

import collectionlog.CollectionLogPlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class InfiniteLavaBucket {

    private static final String DISPLAY_NAME = "§6§lInfinite Lava Bucket";
    private static final List<String> LORE = List.of(
        "§7This bucket never runs out of lava!",
        "§7Use it to place lava or as fuel.",
        "",
        "§eAwarded from: §6Buckets Collection"
    );

    private final NamespacedKey key;

    public InfiniteLavaBucket(CollectionLogPlugin plugin) {
        this.key = new NamespacedKey(plugin, "infinite_lava_bucket");
    }

    /**
     * Creates a new infinite lava bucket item.
     */
    public ItemStack createItem() {
        ItemStack item = new ItemStack(Material.LAVA_BUCKET);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(DISPLAY_NAME);
            meta.setLore(LORE);

            // Mark as infinite using PersistentDataContainer
            meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);

            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Checks if an item is an infinite lava bucket.
     */
    public boolean isInfiniteLavaBucket(ItemStack item) {
        if (item == null || item.getType() != Material.LAVA_BUCKET) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        return meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }
}
