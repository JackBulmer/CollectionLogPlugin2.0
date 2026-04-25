package collectionlog.gui;

import collectionlog.CollectionLogPlugin;
import collectionlog.tags.Tag;
import collectionlog.tags.TagManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * GUI for viewing and equipping player tags.
 */
public class TagsGUI implements Listener {
    private final CollectionLogPlugin plugin;
    private final TagManager tagManager;
    private final InventoryHolder guiInventoryHolder = new TagsGuiHolder();
    private final Map<UUID, TagsGUIState> guiStates = new HashMap<>();

    private static final int INVENTORY_SIZE = 54;
    private static final int PAGE_SIZE = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_FILTER = 49;
    private static final int SLOT_NEXT = 53;

    public TagsGUI(CollectionLogPlugin plugin, TagManager tagManager) {
        this.plugin = plugin;
        this.tagManager = tagManager;
    }

    public void openTagsGUI(Player player) {
        openTagsGUI(player, 0, false);
    }

    private void openTagsGUI(Player player, int page, boolean showUnlockedOnly) {
        UUID playerId = player.getUniqueId();
        guiStates.put(playerId, new TagsGUIState(page, showUnlockedOnly));

        CompletableFuture<List<String>> completedFuture = tagManager.getCompletedCollections(playerId);
        CompletableFuture<String> prefixFuture = tagManager.getPlayerPrefix(playerId);
        CompletableFuture<String> suffixFuture = tagManager.getPlayerSuffix(playerId);

        CompletableFuture.allOf(completedFuture, prefixFuture, suffixFuture).thenRun(() -> {
            List<String> completedCollections = completedFuture.join();
            String prefix = prefixFuture.join();
            String suffix = suffixFuture.join();

            List<Tag> tagsToShow = showUnlockedOnly
                    ? tagManager.getUnlockedTags(completedCollections)
                    : tagManager.getAllTags();

            int totalPages = Math.max(1, (int) Math.ceil((double) tagsToShow.size() / PAGE_SIZE));
            int clampedPage = Math.max(0, Math.min(page, totalPages - 1));

            // Update state with clamped page
            guiStates.put(playerId, new TagsGUIState(clampedPage, showUnlockedOnly));

            int startIndex = clampedPage * PAGE_SIZE;
            int endIndex = Math.min(startIndex + PAGE_SIZE, tagsToShow.size());

            Inventory inv = Bukkit.createInventory(guiInventoryHolder, INVENTORY_SIZE, ChatColor.GOLD + "Tags");

            for (int i = startIndex; i < endIndex; i++) {
                Tag tag = tagsToShow.get(i);
                boolean isUnlocked = tagManager.isTagUnlocked(tag, completedCollections);
                boolean isEquipped = tag.isPrefix()
                        ? tag.getName().equals(prefix)
                        : tag.getName().equals(suffix);

                inv.setItem(i - startIndex, createTagItem(tag, isUnlocked, isEquipped));
            }

            if (clampedPage > 0) {
                inv.setItem(SLOT_PREV, createNavigationButton("§6◀ Previous Page"));
            }
            if (clampedPage < totalPages - 1) {
                inv.setItem(SLOT_NEXT, createNavigationButton("§6Next Page ▶"));
            }
            inv.setItem(SLOT_FILTER, createFilterButton(showUnlockedOnly));

            // Inventory must be opened on the main thread
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    player.openInventory(inv);
                }
            });
        });
    }

    private ItemStack createTagItem(Tag tag, boolean isUnlocked, boolean isEquipped) {
        ItemStack item = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(tag.getName());

            List<String> lore = new ArrayList<>();
            lore.add(tag.isPrefix() ? "§ePrefix Tag" : "§bSuffix Tag");
            lore.add(isEquipped ? "§a✓ Currently Equipped" : "§7Not Equipped");
            lore.add(isUnlocked ? "§a✓ Unlocked" : "§c✗ Locked");

            if (!isUnlocked && tag.hasRequirement()) {
                lore.add("§7Requires: §e" + formatCollectionName(tag.getRequiredCollection()));
            }

            if (isUnlocked) {
                lore.add("");
                lore.add(isEquipped ? "§7Click to unequip" : "§eClick to equip");
            }

            meta.setLore(lore);

            if (isEquipped) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }

            item.setItemMeta(meta);
        }

        return item;
    }

    private ItemStack createNavigationButton(String name) {
        ItemStack button = new ItemStack(Material.ARROW);
        ItemMeta meta = button.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            button.setItemMeta(meta);
        }
        return button;
    }

    private ItemStack createFilterButton(boolean showUnlockedOnly) {
        ItemStack button = new ItemStack(showUnlockedOnly ? Material.PAPER : Material.HOPPER);
        ItemMeta meta = button.getItemMeta();
        if (meta != null) {
            if (showUnlockedOnly) {
                meta.setCustomModelData(100001);
            }
            meta.setDisplayName(showUnlockedOnly
                    ? "§eShowing: §aUnlocked Only"
                    : "§eShowing: §fAll Tags");
            List<String> lore = new ArrayList<>();
            lore.add("§7Click to toggle tag filter");
            meta.setLore(lore);
            button.setItemMeta(meta);
        }
        return button;
    }

    private String formatCollectionName(String collectionId) {
        if (collectionId == null || collectionId.isEmpty()) return "None";
        return collectionId.substring(0, 1).toUpperCase() + collectionId.substring(1);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof TagsGuiHolder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) {
            return;
        }

        UUID playerId = player.getUniqueId();
        TagsGUIState state = guiStates.get(playerId);
        if (state == null) {
            return;
        }

        int slot = event.getRawSlot();

        if (slot == SLOT_PREV) {
            openTagsGUI(player, state.page - 1, state.showUnlockedOnly);
            return;
        }

        if (slot == SLOT_NEXT) {
            openTagsGUI(player, state.page + 1, state.showUnlockedOnly);
            return;
        }

        if (slot == SLOT_FILTER) {
            openTagsGUI(player, 0, !state.showUnlockedOnly);
            return;
        }

        if (slot < PAGE_SIZE) {
            handleTagClick(player, clicked);
        }
    }

    private void handleTagClick(Player player, ItemStack clicked) {
        UUID playerId = player.getUniqueId();
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        String tagName = meta.getDisplayName();
        List<String> lore = meta.getLore();

        if (lore == null || lore.isEmpty()) {
            return;
        }

        boolean isLocked = lore.stream().anyMatch(line -> line.contains("§c✗ Locked"));
        if (isLocked) {
            player.sendMessage("§cThis tag is locked. Complete the required collection to unlock it.");
            return;
        }

        boolean isEquipped = lore.stream().anyMatch(line -> line.contains("§a✓ Currently Equipped"));
        boolean isPrefix = lore.stream().anyMatch(line -> line.contains("§ePrefix Tag"));
        TagsGUIState state = guiStates.get(playerId);

        if (isPrefix) {
            String newPrefix = isEquipped ? "" : tagName;
            tagManager.setPlayerPrefix(playerId, newPrefix).thenRun(() -> {
                if (isEquipped) {
                    player.sendMessage("§7Prefix tag unequipped.");
                } else {
                    player.sendMessage("§aPrefix tag set to: " + tagName);
                }
                updatePlayerDisplayName(player);
                if (state != null) {
                    openTagsGUI(player, state.page, state.showUnlockedOnly);
                }
            });
        } else {
            String newSuffix = isEquipped ? "" : tagName;
            tagManager.setPlayerSuffix(playerId, newSuffix).thenRun(() -> {
                if (isEquipped) {
                    player.sendMessage("§7Suffix tag unequipped.");
                } else {
                    player.sendMessage("§aSuffix tag set to: " + tagName);
                }
                updatePlayerDisplayName(player);
                if (state != null) {
                    openTagsGUI(player, state.page, state.showUnlockedOnly);
                }
            });
        }
    }

    private void updatePlayerDisplayName(Player player) {
        UUID playerId = player.getUniqueId();
        tagManager.getPlayerPrefix(playerId).thenAccept(prefix ->
                tagManager.getPlayerSuffix(playerId).thenAccept(suffix -> {
                    String displayName = prefix + player.getName() + suffix;
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (player.isOnline()) {
                            player.setDisplayName(displayName);
                            player.setPlayerListName(displayName);
                        }
                    });
                })
        );
    }

    public void cleanup(UUID playerId) {
        guiStates.remove(playerId);
    }

    private static class TagsGuiHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private static class TagsGUIState {
        int page;
        boolean showUnlockedOnly;

        TagsGUIState(int page, boolean showUnlockedOnly) {
            this.page = page;
            this.showUnlockedOnly = showUnlockedOnly;
        }
    }
}
