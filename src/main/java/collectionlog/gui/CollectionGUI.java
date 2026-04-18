package collectionlog.gui;

import collectionlog.CollectionLogPlugin;
import collectionlog.collections.CollectionDefinition;
import collectionlog.collections.SubCollectionDefinition;
import collectionlog.database.dao.PlayerCollectionDAO;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Main GUI manager for collection views and navigation.
 */
public class CollectionGUI implements Listener {
    private static final String BLANK_TITLE = " ";

    private static final int INVENTORY_SIZE = 54;
    private static final int MAIN_PAGE_SIZE = 45;
    private static final int COLLECTION_PAGE_SIZE = 27;
    private static final int SUB_PAGE_SIZE = 36;

    private static final int SLOT_BACK = 45;
    private static final int SLOT_FILTER = 50;
    private static final int SLOT_PREV = 52;
    private static final int SLOT_NEXT = 53;
    private static final int SLOT_INFO = 4;

    private final CollectionLogPlugin plugin;
    private final PlayerCollectionDAO playerCollectionDAO;
    private final InventoryHolder guiInventoryHolder = new CollectionGuiHolder();
    private final Map<UUID, GUIState> guiStates = new HashMap<>();

    public CollectionGUI(CollectionLogPlugin plugin) {
        this.plugin = plugin;
        this.playerCollectionDAO = plugin.getPlayerCollectionDAO();
    }

    public void openMainMenu(Player player) {
        openMainMenu(player, 0, false);
    }

    public boolean openByQuery(Player player, String query) {
        String normalized = normalize(query);
        if (normalized.isBlank()) {
            openMainMenu(player);
            return true;
        }

        String collectionId = findCollectionIdByQuery(query);
        if (collectionId != null) {
            openCollectionView(player, collectionId, 0, false);
            return true;
        }

        SubCollectionMatch subMatch = findSubCollectionByQuery(query);
        if (subMatch != null) {
            openSubCollectionView(player, subMatch.collectionId(), subMatch.subCollectionId(), 0, false);
            return true;
        }

        return false;
    }

    public void openCollectionView(Player player, String collectionId, int page, boolean showUncollectedOnly) {
        CollectionDefinition collection = getCollectionById(collectionId);
        if (collection == null) {
            player.sendMessage(ChatColor.RED + "Collection not found: " + collectionId);
            openMainMenu(player);
            return;
        }

        Set<String> collected = getCollectedSet(player, collectionId);
        List<String> items = new ArrayList<>(collection.getItems());
        if (showUncollectedOnly) {
            items.removeIf(collected::contains);
        }

        PageSlice<String> pageSlice = slice(items, page, COLLECTION_PAGE_SIZE);

        Inventory inventory = Bukkit.createInventory(guiInventoryHolder, INVENTORY_SIZE, BLANK_TITLE);

        int total = collection.getItems().size();
        int collectedCount = total - countMissing(collection.getItems(), collected);
        inventory.setItem(SLOT_INFO, GUIHelper.createCollectionIcon(collection.getId(), collection, collectedCount, total));

        Map<Integer, SlotAction> slotActions = new HashMap<>();

        int subSlot = 9;
        for (SubCollectionDefinition subCollection : collection.getSubCollections()) {
            if (subSlot > 17) {
                break;
            }

            int subCollected = countCollected(subCollection.getItems(), collected);
            ItemStack subIcon = createSubCollectionIcon(collection.getId(), subCollection, subCollected);
            inventory.setItem(subSlot, subIcon);
            slotActions.put(subSlot, SlotAction.openSubCollection(collection.getId(), subCollection.getId()));
            subSlot++;
        }

        int[] itemSlots = buildSlotRange(18, 44);
        for (int i = 0; i < pageSlice.pageItems().size() && i < itemSlots.length; i++) {
            String signature = pageSlice.pageItems().get(i);
            boolean isCollected = collected.contains(signature);
            inventory.setItem(itemSlots[i], GUIHelper.createItemDisplay(signature, isCollected, collection.getName(), collectionId));
        }

        inventory.setItem(SLOT_BACK, GUIHelper.createNavigationButton("Back", 24001));
        inventory.setItem(SLOT_FILTER, GUIHelper.createFilterButton(showUncollectedOnly));
        if (pageSlice.hasPrevious()) {
            inventory.setItem(SLOT_PREV, GUIHelper.createNavigationButton("Previous", 100002));
        }
        if (pageSlice.hasNext()) {
            inventory.setItem(SLOT_NEXT, GUIHelper.createNavigationButton("Next", 100003));
        }

        player.openInventory(inventory);
        guiStates.put(player.getUniqueId(), new GUIState(
                ViewType.COLLECTION,
                collection.getId(),
                null,
                pageSlice.page(),
                showUncollectedOnly,
                pageSlice.hasPrevious(),
                pageSlice.hasNext(),
                slotActions
        ));
    }

    public void openSubCollectionView(Player player, String collectionId, String subCollectionId, int page, boolean showUncollectedOnly) {
        CollectionDefinition collection = getCollectionById(collectionId);
        if (collection == null) {
            player.sendMessage(ChatColor.RED + "Collection not found: " + collectionId);
            openMainMenu(player);
            return;
        }

        SubCollectionDefinition subCollection = getSubCollectionById(collection, subCollectionId);
        if (subCollection == null) {
            player.sendMessage(ChatColor.RED + "Sub-collection not found: " + subCollectionId);
            openCollectionView(player, collectionId, 0, showUncollectedOnly);
            return;
        }

        Set<String> collected = getCollectedSet(player, collectionId);
        List<String> items = new ArrayList<>(subCollection.getItems());
        if (showUncollectedOnly) {
            items.removeIf(collected::contains);
        }

        PageSlice<String> pageSlice = slice(items, page, SUB_PAGE_SIZE);

        Inventory inventory = Bukkit.createInventory(guiInventoryHolder, INVENTORY_SIZE, BLANK_TITLE);

        int subCollected = countCollected(subCollection.getItems(), collected);
        ItemStack info = createSubCollectionIcon(collectionId, subCollection, subCollected);
        inventory.setItem(SLOT_INFO, info);

        int[] itemSlots = buildSlotRange(9, 44);
        for (int i = 0; i < pageSlice.pageItems().size() && i < itemSlots.length; i++) {
            String signature = pageSlice.pageItems().get(i);
            boolean isCollected = collected.contains(signature);
            inventory.setItem(itemSlots[i], GUIHelper.createItemDisplay(signature, isCollected, subCollection.getName(), collectionId));
        }

        inventory.setItem(SLOT_BACK, GUIHelper.createNavigationButton("Back to Parent", 24001));
        inventory.setItem(SLOT_FILTER, GUIHelper.createFilterButton(showUncollectedOnly));
        if (pageSlice.hasPrevious()) {
            inventory.setItem(SLOT_PREV, GUIHelper.createNavigationButton("Previous", 100002));
        }
        if (pageSlice.hasNext()) {
            inventory.setItem(SLOT_NEXT, GUIHelper.createNavigationButton("Next", 100003));
        }

        player.openInventory(inventory);
        guiStates.put(player.getUniqueId(), new GUIState(
                ViewType.SUB_COLLECTION,
                collectionId,
                subCollection.getId(),
                pageSlice.page(),
                showUncollectedOnly,
                pageSlice.hasPrevious(),
                pageSlice.hasNext(),
                Map.of()
        ));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        GUIState state = guiStates.get(player.getUniqueId());
        if (state == null || !isCollectionGuiInventory(event.getView().getTopInventory())) {
            return;
        }

        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }
        int slot = event.getRawSlot();

        if (slot == SLOT_BACK) {
            handleBack(player, state);
            return;
        }

        if (slot == SLOT_FILTER && state.viewType() != ViewType.MAIN) {
            handleFilterToggle(player, state);
            return;
        }

        if (slot == SLOT_PREV && state.hasPrevious()) {
            navigate(player, state, state.page() - 1);
            return;
        }

        if (slot == SLOT_NEXT && state.hasNext()) {
            navigate(player, state, state.page() + 1);
            return;
        }

        SlotAction action = state.slotActions().get(slot);
        if (action == null) {
            return;
        }

        if (action.type() == ActionType.OPEN_COLLECTION) {
            openCollectionView(player, action.collectionId(), 0, state.showUncollectedOnly());
        } else if (action.type() == ActionType.OPEN_SUB_COLLECTION) {
            openSubCollectionView(player, action.collectionId(), action.subCollectionId(), 0, state.showUncollectedOnly());
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (isCollectionGuiInventory(event.getView().getTopInventory())) {
            guiStates.remove(event.getPlayer().getUniqueId());
        }
    }

    private void openMainMenu(Player player, int page, boolean showUncollectedOnly) {
        Map<String, CollectionDefinition> allCollections = getAllCollections();

        List<CollectionEntry> entries = new ArrayList<>();
        for (Map.Entry<String, CollectionDefinition> entry : allCollections.entrySet()) {
            String collectionId = entry.getKey();
            CollectionDefinition collection = entry.getValue();

            Set<String> collected = getCollectedSet(player, collectionId);
            int total = collection.getItems().size();
            int collectedCount = countCollected(collection.getItems(), collected);

            if (showUncollectedOnly && collectedCount >= total && total > 0) {
                continue;
            }

            entries.add(new CollectionEntry(collectionId, collection, collectedCount, total));
        }

        entries.sort(Comparator.comparing(e -> e.collection().getName(), String.CASE_INSENSITIVE_ORDER));

        PageSlice<CollectionEntry> pageSlice = slice(entries, page, MAIN_PAGE_SIZE);
        Inventory inventory = Bukkit.createInventory(guiInventoryHolder, INVENTORY_SIZE, BLANK_TITLE);

        Map<Integer, SlotAction> slotActions = new HashMap<>();
        for (int i = 0; i < pageSlice.pageItems().size() && i < MAIN_PAGE_SIZE; i++) {
            CollectionEntry entry = pageSlice.pageItems().get(i);
            Material iconMaterial = getMaterialForCollection(entry.collectionId());
            inventory.setItem(i, GUIHelper.createCollectionIcon(entry.collectionId(), entry.collection(), entry.collected(), entry.total(), iconMaterial));
            slotActions.put(i, SlotAction.openCollection(entry.collectionId()));
        }

        if (pageSlice.hasPrevious()) {
            inventory.setItem(SLOT_PREV, GUIHelper.createNavigationButton("Previous", 100002));
        }
        if (pageSlice.hasNext()) {
            inventory.setItem(SLOT_NEXT, GUIHelper.createNavigationButton("Next", 100003));
        }

        player.openInventory(inventory);
        guiStates.put(player.getUniqueId(), new GUIState(
                ViewType.MAIN,
                null,
                null,
                pageSlice.page(),
                showUncollectedOnly,
                pageSlice.hasPrevious(),
                pageSlice.hasNext(),
                slotActions
        ));
    }

    private void handleBack(Player player, GUIState state) {
        switch (state.viewType()) {
            case MAIN -> player.closeInventory();
            case COLLECTION -> openMainMenu(player, 0, false);
            case SUB_COLLECTION -> openCollectionView(player, state.collectionId(), 0, state.showUncollectedOnly());
        }
    }

    private void handleFilterToggle(Player player, GUIState state) {
        boolean nextFilter = !state.showUncollectedOnly();
        navigate(player, state, 0, nextFilter);
    }

    private void navigate(Player player, GUIState state, int page) {
        navigate(player, state, page, state.showUncollectedOnly());
    }

    private void navigate(Player player, GUIState state, int page, boolean showUncollectedOnly) {
        switch (state.viewType()) {
            case MAIN -> openMainMenu(player, page, showUncollectedOnly);
            case COLLECTION -> openCollectionView(player, state.collectionId(), page, showUncollectedOnly);
            case SUB_COLLECTION -> openSubCollectionView(player, state.collectionId(), state.subCollectionId(), page, showUncollectedOnly);
        }
    }

    private boolean isCollectionGuiInventory(Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof CollectionGuiHolder;
    }

    private CollectionDefinition getCollectionById(String collectionId) {
        CollectionDefinition standard = plugin.getCollectionLoader().getCollection(collectionId);
        if (standard != null) {
            return standard;
        }

        return plugin.getSpecialCollectionLoader().getSpecialCollection(collectionId);
    }

    private Map<String, CollectionDefinition> getAllCollections() {
        Map<String, CollectionDefinition> all = new LinkedHashMap<>();
        all.putAll(plugin.getCollectionLoader().getCollections());
        all.putAll(plugin.getSpecialCollectionLoader().getSpecialCollections());
        return all;
    }

    private Set<String> getCollectedSet(Player player, String collectionId) {
        if (playerCollectionDAO == null) {
            return Set.of();
        }

        try {
            return new HashSet<>(playerCollectionDAO.getCollectedItems(player.getUniqueId(), collectionId).join());
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Failed to load collected items for GUI: " + e.getMessage());
            return Set.of();
        }
    }

    private SubCollectionDefinition getSubCollectionById(CollectionDefinition collection, String subCollectionId) {
        String target = normalize(subCollectionId);
        for (SubCollectionDefinition subCollection : collection.getSubCollections()) {
            String id = normalize(subCollection.getId());
            String name = normalize(subCollection.getName());
            String shortName = normalize(extractShortSubName(subCollection.getId()));

            if (target.equals(id) || target.equals(name) || target.equals(shortName)) {
                return subCollection;
            }
        }
        return null;
    }

    private String findCollectionIdByQuery(String query) {
        String target = normalize(query);

        for (Map.Entry<String, CollectionDefinition> entry : getAllCollections().entrySet()) {
            String id = normalize(entry.getKey());
            String name = normalize(entry.getValue().getName());
            if (target.equals(id) || target.equals(name)) {
                return entry.getKey();
            }

            if (target.equals(normalizeAlias(entry.getKey()))) {
                return entry.getKey();
            }
        }

        return null;
    }

    private SubCollectionMatch findSubCollectionByQuery(String query) {
        String target = normalize(query);

        for (Map.Entry<String, CollectionDefinition> entry : plugin.getCollectionLoader().getCollections().entrySet()) {
            for (SubCollectionDefinition subCollection : entry.getValue().getSubCollections()) {
                String id = normalize(subCollection.getId());
                String name = normalize(subCollection.getName());
                String shortName = normalize(extractShortSubName(subCollection.getId()));
                if (target.equals(id) || target.equals(name) || target.equals(shortName)
                        || target.equals(normalizeAlias(subCollection.getName()))) {
                    return new SubCollectionMatch(entry.getKey(), subCollection.getId());
                }
            }
        }

        return null;
    }

    private String extractShortSubName(String subCollectionId) {
        if (subCollectionId == null) {
            return "";
        }

        int separator = subCollectionId.indexOf('_');
        if (separator >= 0 && separator < subCollectionId.length() - 1) {
            return subCollectionId.substring(separator + 1);
        }
        return subCollectionId;
    }

    private int countCollected(List<String> sourceItems, Set<String> collectedSet) {
        int count = 0;
        for (String item : sourceItems) {
            if (collectedSet.contains(item)) {
                count++;
            }
        }
        return count;
    }

    private int countMissing(List<String> sourceItems, Set<String> collectedSet) {
        return sourceItems.size() - countCollected(sourceItems, collectedSet);
    }

    private ItemStack createSubCollectionIcon(String collectionId, SubCollectionDefinition subCollection, int collected) {
        String shortName = extractShortSubName(subCollection.getId());
        Material iconMaterial = getMaterialForCollection(collectionId + ":" + shortName);
        ItemStack icon = new ItemStack(iconMaterial);
        ItemMeta meta = icon.getItemMeta();
        if (meta == null) {
            return icon;
        }

        int total = subCollection.getTotalItems();
        double progress = total <= 0 ? 0.0 : (collected * 100.0) / total;
        meta.setDisplayName(ChatColor.GOLD + subCollection.getName() + ChatColor.GRAY + " (" + collected + "/" + total + ")");
        meta.setLore(List.of(
                ChatColor.GRAY + "Progress: " + String.format(Locale.ROOT, "%.2f%%", progress),
                GUIHelper.formatProgressBar(progress),
                ChatColor.DARK_GRAY + "Click to open sub-collection"
        ));
        icon.setItemMeta(meta);
        if (progress >= 100.0) {
            GUIHelper.applyGlow(icon);
        }
        return icon;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).trim().replace('_', ' ').replaceAll("\\s+", " ");
    }

    private String normalizeAlias(String value) {
        return normalize(value).replace(" ", "");
    }

    private Material getMaterialForCollection(String collectionName) {
        String normalized = normalize(collectionName);
        if (normalized.contains(":")) {
            String[] parts = normalized.split(":", 2);
            String parent = parts[0];
            String sub = parts[1];

            if ("wood".equals(parent)) {
                return switch (sub) {
                    case "oak" -> Material.OAK_LOG;
                    case "spruce" -> Material.SPRUCE_LOG;
                    case "birch" -> Material.BIRCH_LOG;
                    case "jungle" -> Material.JUNGLE_LOG;
                    case "acacia" -> Material.ACACIA_LOG;
                    case "dark oak" -> Material.DARK_OAK_LOG;
                    case "mangrove" -> Material.MANGROVE_LOG;
                    case "cherry" -> Material.CHERRY_LOG;
                    case "bamboo" -> Material.BAMBOO_BLOCK;
                    case "pale oak" -> Material.PALE_OAK_LOG;
                    default -> Material.OAK_LOG;
                };
            }
        }

        return switch (normalized) {
            case "wood" -> Material.OAK_LOG;
            case "ores" -> Material.IRON_ORE;
            case "stones" -> Material.STONE;
            case "nature" -> Material.GRASS_BLOCK;
            case "food" -> Material.COOKED_BEEF;
            case "combat", "weapons and tools" -> Material.DIAMOND_SWORD;
            case "armour" -> Material.DIAMOND_CHESTPLATE;
            case "nether" -> Material.NETHERRACK;
            case "end" -> Material.END_STONE;
            case "ocean" -> Material.HEART_OF_THE_SEA;
            case "dyes" -> Material.RED_DYE;
            case "fishing", "jb fishing plus" -> Material.COOKED_COD;
            case "enchantments" -> Material.ENCHANTED_BOOK;
            case "potions" -> Material.POTION;
            case "mob heads" -> Material.PLAYER_HEAD;
            case "armour trims", "armor trims" -> Material.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE;
            case "tcg" -> Material.PAPER;
            case "spawn eggs" -> Material.ZOMBIE_SPAWN_EGG;
            default -> Material.PAPER;
        };
    }

    private int[] buildSlotRange(int start, int endInclusive) {
        int[] slots = new int[(endInclusive - start) + 1];
        for (int i = 0; i < slots.length; i++) {
            slots[i] = start + i;
        }
        return slots;
    }

    private <T> PageSlice<T> slice(List<T> source, int requestedPage, int pageSize) {
        if (source.isEmpty()) {
            return new PageSlice<>(List.of(), 0, false, false);
        }

        int maxPage = Math.max(0, (source.size() - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, maxPage));

        int from = page * pageSize;
        int to = Math.min(source.size(), from + pageSize);

        List<T> pageItems = source.subList(from, to);
        boolean hasPrevious = page > 0;
        boolean hasNext = page < maxPage;
        return new PageSlice<>(pageItems, page, hasPrevious, hasNext);
    }

    private enum ViewType {
        MAIN,
        COLLECTION,
        SUB_COLLECTION
    }

    private enum ActionType {
        OPEN_COLLECTION,
        OPEN_SUB_COLLECTION
    }

    private record SlotAction(ActionType type, String collectionId, String subCollectionId) {
        static SlotAction openCollection(String collectionId) {
            return new SlotAction(ActionType.OPEN_COLLECTION, collectionId, null);
        }

        static SlotAction openSubCollection(String collectionId, String subCollectionId) {
            return new SlotAction(ActionType.OPEN_SUB_COLLECTION, collectionId, subCollectionId);
        }
    }

    private record CollectionEntry(String collectionId, CollectionDefinition collection, int collected, int total) {
    }

    private record GUIState(
            ViewType viewType,
            String collectionId,
            String subCollectionId,
            int page,
            boolean showUncollectedOnly,
            boolean hasPrevious,
            boolean hasNext,
            Map<Integer, SlotAction> slotActions
    ) {
    }

    private record PageSlice<T>(List<T> pageItems, int page, boolean hasPrevious, boolean hasNext) {
    }

    private record SubCollectionMatch(String collectionId, String subCollectionId) {
    }

    private static final class CollectionGuiHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
