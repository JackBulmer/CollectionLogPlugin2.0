package collectionlog.perks.enderchest.holders;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public class LargerEnderChestHolder implements InventoryHolder {
    private final UUID playerId;

    public LargerEnderChestHolder(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
