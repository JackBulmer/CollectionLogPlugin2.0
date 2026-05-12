package collectionlog.perks.enderchest.holders;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public class CondensingEnderChestHolder implements InventoryHolder {
    private final UUID playerId;
    /** Set immediately after Bukkit creates the inventory for this holder. */
    private Inventory inventory;

    public CondensingEnderChestHolder(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        if (inventory == null) {
            throw new IllegalStateException("CondensingEnderChestHolder inventory has not been initialized yet");
        }
        return inventory;
    }
}
