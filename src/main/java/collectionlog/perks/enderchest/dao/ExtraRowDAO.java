package collectionlog.perks.enderchest.dao;

import collectionlog.CollectionLogPlugin;
import collectionlog.database.DatabaseManager;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ExtraRowDAO {
    private static final int MIN_EXTRA_SLOT = 27;
    private static final int MAX_EXTRA_SLOT = 35;

    private final CollectionLogPlugin plugin;
    private final DatabaseManager databaseManager;

    public ExtraRowDAO(CollectionLogPlugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    public Map<Integer, ItemStack> loadExtraRow(UUID playerId) {
        String sql = "SELECT slot, item_type, item_amount, item_data FROM ender_chest_extra WHERE player_uuid = ?";
        Map<Integer, ItemStack> items = new HashMap<>();

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerId.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int slot = rs.getInt("slot");
                    if (slot < MIN_EXTRA_SLOT || slot > MAX_EXTRA_SLOT) {
                        continue;
                    }

                    ItemStack item = deserializeItem(
                            rs.getString("item_type"),
                            rs.getInt("item_amount"),
                            rs.getString("item_data")
                    );
                    if (item != null && !item.getType().isAir()) {
                        items.put(slot, item);
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load extra ender chest row: " + e.getMessage());
        }

        return items;
    }

    public void saveExtraRow(UUID playerId, Map<Integer, ItemStack> items) {
        deleteExtraRow(playerId);
        if (items.isEmpty()) {
            return;
        }

        String sql = "INSERT INTO ender_chest_extra (player_uuid, slot, item_type, item_amount, item_data) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (Map.Entry<Integer, ItemStack> entry : items.entrySet()) {
                int slot = entry.getKey();
                if (slot < MIN_EXTRA_SLOT || slot > MAX_EXTRA_SLOT) {
                    continue;
                }

                ItemStack item = entry.getValue();
                if (item == null || item.getType().isAir()) {
                    continue;
                }

                stmt.setString(1, playerId.toString());
                stmt.setInt(2, slot);
                stmt.setString(3, item.getType().name());
                stmt.setInt(4, item.getAmount());
                stmt.setString(5, serializeItem(item));
                stmt.addBatch();
            }
            stmt.executeBatch();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save extra ender chest row: " + e.getMessage());
        }
    }

    public boolean hasItemsInExtraRow(UUID playerId) {
        String sql = "SELECT 1 FROM ender_chest_extra WHERE player_uuid = ? LIMIT 1";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to check extra ender chest row: " + e.getMessage());
            return false;
        }
    }

    public void deleteExtraRow(UUID playerId) {
        String sql = "DELETE FROM ender_chest_extra WHERE player_uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerId.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to delete extra ender chest row: " + e.getMessage());
        }
    }

    private String serializeItem(ItemStack item) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
                dataOutput.writeObject(item);
            }
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to serialize ender chest item: " + e.getMessage());
            return "";
        }
    }

    private ItemStack deserializeItem(String itemType, int amount, String itemData) {
        if (itemData == null || itemData.isBlank()) {
            Material material = Material.getMaterial(itemType);
            return material == null ? null : new ItemStack(material, amount);
        }

        try {
            byte[] data = Base64.getDecoder().decode(itemData);
            try (BukkitObjectInputStream dataInput = new BukkitObjectInputStream(new ByteArrayInputStream(data))) {
                return (ItemStack) dataInput.readObject();
            }
        } catch (Exception e) {
            Material material = Material.getMaterial(itemType);
            return material == null ? null : new ItemStack(material, amount);
        }
    }
}
