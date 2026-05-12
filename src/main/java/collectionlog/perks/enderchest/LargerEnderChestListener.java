package collectionlog.perks.enderchest;

import collectionlog.CollectionLogPlugin;
import collectionlog.perks.PerkManager;
import collectionlog.perks.enderchest.holders.CondensingEnderChestHolder;
import collectionlog.perks.enderchest.holders.LargerEnderChestHolder;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class LargerEnderChestListener implements Listener {
    private static final String PERK_ID = "larger_ender_chest";
    private static final int EXTRA_START = 27;
    private static final int EXTRA_END = 35;
    private static final int TOP_SIZE = 36;

    private final PerkManager perkManager;
    private final LargerEnderChestManager manager;

    public LargerEnderChestListener(CollectionLogPlugin plugin, LargerEnderChestManager manager) {
        this.perkManager = plugin.getPerkManager();
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Block clicked = event.getClickedBlock();
        if (clicked == null || clicked.getType() != Material.ENDER_CHEST) {
            return;
        }

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (perkManager.hasUnlocked(playerId, PERK_ID)) {
            event.setCancelled(true);
            manager.openLargerEnderChest(player);
            return;
        }

        if (manager.isCondensingMode(playerId)) {
            event.setCancelled(true);
            manager.openCondensingEnderChest(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        if (event.getInventory().getHolder() instanceof LargerEnderChestHolder holder) {
            manager.saveAndCloseLargerEnderChest(player, event.getInventory(), holder.getPlayerId());
        } else if (event.getInventory().getHolder() instanceof CondensingEnderChestHolder holder) {
            manager.saveAndCloseCondensingEnderChest(player, event.getInventory(), holder.getPlayerId());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof CondensingEnderChestHolder)) {
            return;
        }

        int rawSlot = event.getRawSlot();
        if (rawSlot >= 0 && rawSlot < TOP_SIZE) {
            if (rawSlot >= EXTRA_START && rawSlot <= EXTRA_END) {
                ItemStack cursor = event.getCursor();
                ItemStack current = event.getCurrentItem();

                boolean removingByMouseClick = (cursor == null || cursor.getType().isAir())
                        && current != null && !current.getType().isAir();
                boolean removingByShift = event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY;

                if (!removingByMouseClick && !removingByShift) {
                    event.setCancelled(true);
                    sendRestrictionMessage((Player) event.getWhoClicked());
                }
            }
            return;
        }

        if (event.isShiftClick() && rawSlot >= TOP_SIZE && hasEmptyProtectedSlot(event.getView().getTopInventory())) {
            event.setCancelled(true);
            sendRestrictionMessage((Player) event.getWhoClicked());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof CondensingEnderChestHolder)) {
            return;
        }
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= EXTRA_START && rawSlot <= EXTRA_END) {
                event.setCancelled(true);
                sendRestrictionMessage((Player) event.getWhoClicked());
                return;
            }
        }
    }

    private boolean hasEmptyProtectedSlot(Inventory topInventory) {
        for (int i = EXTRA_START; i <= EXTRA_END; i++) {
            ItemStack item = topInventory.getItem(i);
            if (item == null || item.getType().isAir()) {
                return true;
            }
        }
        return false;
    }

    private void sendRestrictionMessage(Player player) {
        player.sendMessage("§cYou cannot add items to this row. Remove items to exit condensing mode.");
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
    }
}
