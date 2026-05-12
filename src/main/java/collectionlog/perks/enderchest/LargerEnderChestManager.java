package collectionlog.perks.enderchest;

import collectionlog.CollectionLogPlugin;
import collectionlog.perks.enderchest.dao.CondensingModeDAO;
import collectionlog.perks.enderchest.dao.ExtraRowDAO;
import collectionlog.perks.enderchest.holders.CondensingEnderChestHolder;
import collectionlog.perks.enderchest.holders.LargerEnderChestHolder;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LargerEnderChestManager {
    private static final int VANILLA_SIZE = 27;
    private static final int LARGER_SIZE = 36;
    private static final int EXTRA_START = 27;
    private static final int EXTRA_END = 35;
    private static final String ENDER_CHEST_TITLE = "Ender Chest";
    private static final String CONDENSING_TITLE = "Ender Chest §c(Condensing Mode)";

    private final CollectionLogPlugin plugin;
    private final ExtraRowDAO extraRowDAO;
    private final CondensingModeDAO condensingModeDAO;
    private final Set<UUID> condensingPlayers = ConcurrentHashMap.newKeySet();

    public LargerEnderChestManager(CollectionLogPlugin plugin) {
        this.plugin = plugin;
        this.extraRowDAO = new ExtraRowDAO(plugin, plugin.getDatabaseManager());
        this.condensingModeDAO = new CondensingModeDAO(plugin, plugin.getDatabaseManager());
    }

    public void loadCondensingPlayersAsync() {
        plugin.getAsyncTaskManager().runAsync(condensingModeDAO::loadAllCondensingPlayers)
                .thenAccept(players -> plugin.getAsyncTaskManager().runSync(() -> {
                    condensingPlayers.clear();
                    condensingPlayers.addAll(players);
                }));
    }

    public boolean isCondensingMode(UUID playerId) {
        return condensingPlayers.contains(playerId);
    }

    public void openLargerEnderChest(Player player) {
        UUID playerId = player.getUniqueId();
        LargerEnderChestHolder holder = new LargerEnderChestHolder(playerId);
        Inventory inventory = Bukkit.createInventory(holder, LARGER_SIZE, ENDER_CHEST_TITLE);
        holder.setInventory(inventory);

        ItemStack[] vanillaContents = player.getEnderChest().getContents();
        for (int i = 0; i < VANILLA_SIZE && i < vanillaContents.length; i++) {
            inventory.setItem(i, vanillaContents[i]);
        }

        plugin.getAsyncTaskManager().runAsync(() -> extraRowDAO.loadExtraRow(playerId))
                .thenAccept(extraItems -> plugin.getAsyncTaskManager().runSync(() -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    for (Map.Entry<Integer, ItemStack> entry : extraItems.entrySet()) {
                        inventory.setItem(entry.getKey(), entry.getValue());
                    }
                    player.openInventory(inventory);
                    player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1.0f, 1.0f);
                }));
    }

    public void openCondensingEnderChest(Player player) {
        UUID playerId = player.getUniqueId();
        CondensingEnderChestHolder holder = new CondensingEnderChestHolder(playerId);
        Inventory inventory = Bukkit.createInventory(holder, LARGER_SIZE, CONDENSING_TITLE);
        holder.setInventory(inventory);

        ItemStack[] vanillaContents = player.getEnderChest().getContents();
        for (int i = 0; i < VANILLA_SIZE && i < vanillaContents.length; i++) {
            inventory.setItem(i, vanillaContents[i]);
        }

        plugin.getAsyncTaskManager().runAsync(() -> extraRowDAO.loadExtraRow(playerId))
                .thenAccept(extraItems -> plugin.getAsyncTaskManager().runSync(() -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    for (Map.Entry<Integer, ItemStack> entry : extraItems.entrySet()) {
                        inventory.setItem(entry.getKey(), entry.getValue());
                    }
                    player.sendMessage("§c⚠ Ender Chest Condensing Mode");
                    player.sendMessage("§7Your larger ender chest perk was revoked.");
                    player.sendMessage("§7Remove items from the bottom row to return to normal.");
                    player.openInventory(inventory);
                    player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1.0f, 1.0f);
                }));
    }

    public void saveAndCloseLargerEnderChest(Player player, Inventory inventory, UUID playerId) {
        ItemStack[] vanillaContents = new ItemStack[VANILLA_SIZE];
        for (int i = 0; i < VANILLA_SIZE; i++) {
            vanillaContents[i] = inventory.getItem(i);
        }
        player.getEnderChest().setContents(vanillaContents);

        Map<Integer, ItemStack> extraItems = collectExtraItems(inventory);
        plugin.getAsyncTaskManager().runAsync(() -> extraRowDAO.saveExtraRow(playerId, extraItems))
                .exceptionally(throwable -> {
                    plugin.getLogger().severe("Failed to save larger ender chest: " + throwable.getMessage());
                    return null;
                });

        player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_CLOSE, 1.0f, 1.0f);
    }

    public void saveAndCloseCondensingEnderChest(Player player, Inventory inventory, UUID playerId) {
        ItemStack[] vanillaContents = new ItemStack[VANILLA_SIZE];
        for (int i = 0; i < VANILLA_SIZE; i++) {
            vanillaContents[i] = inventory.getItem(i);
        }

        Map<Integer, ItemStack> extraItems = collectExtraItems(inventory);
        if (extraItems.isEmpty()) {
            player.getEnderChest().setContents(vanillaContents);
            plugin.getAsyncTaskManager().runAsync(() -> {
                condensingModeDAO.removeCondensingFlag(playerId);
                extraRowDAO.deleteExtraRow(playerId);
            }).thenRun(() -> plugin.getAsyncTaskManager().runSync(() -> {
                condensingPlayers.remove(playerId);
                if (player.isOnline()) {
                    player.sendMessage("§aEnder chest condensing complete! You now have a normal ender chest.");
                }
            }));
        } else if (canFitInVanillaSlots(vanillaContents, extraItems)) {
            autoCondense(player, playerId, vanillaContents, extraItems);
        } else {
            player.getEnderChest().setContents(vanillaContents);
            plugin.getAsyncTaskManager().runAsync(() -> extraRowDAO.saveExtraRow(playerId, extraItems))
                    .exceptionally(throwable -> {
                        plugin.getLogger().severe("Failed to save condensing ender chest: " + throwable.getMessage());
                        return null;
                    });
        }

        player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_CLOSE, 1.0f, 1.0f);
    }

    public void handlePerkRevoked(UUID playerId) {
        plugin.getAsyncTaskManager().runAsync(() -> extraRowDAO.hasItemsInExtraRow(playerId))
                .thenAccept(hasItems -> {
                    if (hasItems) {
                        plugin.getAsyncTaskManager().runAsync(() -> condensingModeDAO.markPlayerForCondensing(playerId))
                                .thenRun(() -> plugin.getAsyncTaskManager().runSync(() -> {
                                    condensingPlayers.add(playerId);
                                    Player player = Bukkit.getPlayer(playerId);
                                    if (player != null && player.isOnline()) {
                                        player.sendMessage("§c⚠ Larger Ender Chest perk revoked!");
                                        player.sendMessage("§7You have items in your extra ender chest row.");
                                        player.sendMessage("§7Open your ender chest and remove them to return to normal.");
                                    }
                                }));
                        return;
                    }

                    plugin.getAsyncTaskManager().runAsync(() -> {
                        condensingModeDAO.removeCondensingFlag(playerId);
                        extraRowDAO.deleteExtraRow(playerId);
                    }).thenRun(() -> plugin.getAsyncTaskManager().runSync(() -> condensingPlayers.remove(playerId)));
                });
    }

    private Map<Integer, ItemStack> collectExtraItems(Inventory inventory) {
        Map<Integer, ItemStack> extraItems = new HashMap<>();
        for (int i = EXTRA_START; i <= EXTRA_END; i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && !item.getType().isAir()) {
                extraItems.put(i, item.clone());
            }
        }
        return extraItems;
    }

    private boolean canFitInVanillaSlots(ItemStack[] vanillaContents, Map<Integer, ItemStack> extraItems) {
        Inventory condensedInventory = Bukkit.createInventory(null, VANILLA_SIZE);
        condensedInventory.setContents(cloneContents(vanillaContents));

        for (ItemStack extraItem : extraItems.values()) {
            if (extraItem == null || extraItem.getType().isAir()) {
                continue;
            }

            Map<Integer, ItemStack> leftovers = condensedInventory.addItem(extraItem.clone());
            if (!leftovers.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    private void autoCondense(Player player, UUID playerId, ItemStack[] vanillaContents, Map<Integer, ItemStack> extraItems) {
        Inventory condensedInventory = Bukkit.createInventory(null, VANILLA_SIZE);
        condensedInventory.setContents(cloneContents(vanillaContents));

        for (ItemStack extraItem : extraItems.values()) {
            if (extraItem == null || extraItem.getType().isAir()) {
                continue;
            }

            condensedInventory.addItem(extraItem.clone());
        }

        player.getEnderChest().setContents(condensedInventory.getContents());

        plugin.getAsyncTaskManager().runAsync(() -> {
            condensingModeDAO.removeCondensingFlag(playerId);
            extraRowDAO.deleteExtraRow(playerId);
        }).thenRun(() -> plugin.getAsyncTaskManager().runSync(() -> {
            condensingPlayers.remove(playerId);
            if (player.isOnline()) {
                player.sendMessage("§aEnder chest condensed! You now have a normal ender chest.");
            }
        }));
    }

    private ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] clone = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            clone[i] = contents[i] == null ? null : contents[i].clone();
        }
        return clone;
    }
}
