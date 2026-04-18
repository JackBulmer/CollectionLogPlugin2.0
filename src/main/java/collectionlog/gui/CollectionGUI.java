package collectionlog.gui;

import collectionlog.CollectionLogPlugin;
import collectionlog.collections.CollectionDefinition;
import collectionlog.collections.SubCollectionDefinition;
import collectionlog.database.dao.PlayerCollectionDAO;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
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
    private static final int COLLECTION_PAGE_SIZE = 45;
    private static final int SUB_LIST_PAGE_SIZE = 27;
    private static final int SUB_PAGE_SIZE = 45;

    private static final int SLOT_PREV = 45;
    private static final int SLOT_BACK = 48;
    private static final int SLOT_MAIN_CLOSE = 49;
    private static final int SLOT_FILTER = 50;
    private static final int SLOT_NEXT = 53;
    private static final int SLOT_INFO = 49;

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
            openCollectionOrSubCollectionListing(player, collectionId, 0, false);
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
        List<String> items = buildDisplayItems(collectionId, collection.getItems());
        if (showUncollectedOnly) {
            items.removeIf(signature -> isCollectedForDisplay(collectionId, signature, collected));
        }

        PageSlice<String> pageSlice = slice(items, page, COLLECTION_PAGE_SIZE);
        int totalPages = Math.max(1, (items.size() + COLLECTION_PAGE_SIZE - 1) / COLLECTION_PAGE_SIZE);

        Inventory inventory = Bukkit.createInventory(guiInventoryHolder, INVENTORY_SIZE, BLANK_TITLE);

        int total = collection.getItems().size();
        int collectedCount = total - countMissing(collection.getItems(), collected);
        inventory.setItem(SLOT_INFO, GUIHelper.createCollectionIcon(collection.getId(), collection, collectedCount, total));

        int[] itemSlots = buildSlotRange(0, 44);
        for (int i = 0; i < pageSlice.pageItems().size() && i < itemSlots.length; i++) {
            String signature = pageSlice.pageItems().get(i);
            boolean isCollected = isCollectedForDisplay(collectionId, signature, collected);
            inventory.setItem(itemSlots[i], GUIHelper.createItemDisplay(signature, isCollected, collection.getName(), collectionId));
        }

        addNavigationButtons(inventory, pageSlice.page(), totalPages, showUncollectedOnly);

        player.openInventory(inventory);
        guiStates.put(player.getUniqueId(), new GUIState(
                ViewType.COLLECTION,
                collection.getId(),
                null,
                pageSlice.page(),
                showUncollectedOnly,
                pageSlice.hasPrevious(),
                pageSlice.hasNext(),
                Map.of()
        ));
    }

    public void openSubCollectionListingView(Player player, String collectionId, int page, boolean showUncollectedOnly) {
        CollectionDefinition collection = getCollectionById(collectionId);
        if (collection == null) {
            player.sendMessage(ChatColor.RED + "Collection not found: " + collectionId);
            openMainMenu(player);
            return;
        }

        Set<String> collected = getCollectedSet(player, collectionId);
        List<SubCollectionDefinition> subCollections = new ArrayList<>(collection.getSubCollections());
        if (showUncollectedOnly) {
            subCollections.removeIf(subCollection -> countCollected(subCollection.getItems(), collected) >= subCollection.getTotalItems());
        }

        PageSlice<SubCollectionDefinition> pageSlice = slice(subCollections, page, SUB_LIST_PAGE_SIZE);
        int totalPages = Math.max(1, (subCollections.size() + SUB_LIST_PAGE_SIZE - 1) / SUB_LIST_PAGE_SIZE);

        Inventory inventory = Bukkit.createInventory(guiInventoryHolder, INVENTORY_SIZE, BLANK_TITLE);
        Map<Integer, SlotAction> slotActions = new HashMap<>();

        for (int i = 0; i < pageSlice.pageItems().size() && i < SUB_LIST_PAGE_SIZE; i++) {
            SubCollectionDefinition subCollection = pageSlice.pageItems().get(i);
            int subCollected = countCollected(subCollection.getItems(), collected);
            ItemStack subIcon = createSubCollectionIcon(collectionId, subCollection, subCollected);
            int slot = 18 + i;
            inventory.setItem(slot, subIcon);
            slotActions.put(slot, SlotAction.openSubCollection(collectionId, subCollection.getId()));
        }

        int total = collection.getItems().size();
        int collectedCount = total - countMissing(collection.getItems(), collected);
        inventory.setItem(SLOT_INFO, GUIHelper.createCollectionIcon(collection.getId(), collection, collectedCount, total));

        addNavigationButtons(inventory, pageSlice.page(), totalPages, showUncollectedOnly);

        player.openInventory(inventory);
        guiStates.put(player.getUniqueId(), new GUIState(
                ViewType.SUB_COLLECTION_LIST,
                collectionId,
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
            openCollectionOrSubCollectionListing(player, collectionId, 0, showUncollectedOnly);
            return;
        }

        Set<String> collected = getCollectedSet(player, collectionId);
        List<String> items = new ArrayList<>(subCollection.getItems());
        if (showUncollectedOnly) {
            items.removeIf(signature -> isCollectedForDisplay(collectionId, signature, collected));
        }

        PageSlice<String> pageSlice = slice(items, page, SUB_PAGE_SIZE);
        int totalPages = Math.max(1, (items.size() + SUB_PAGE_SIZE - 1) / SUB_PAGE_SIZE);

        Inventory inventory = Bukkit.createInventory(guiInventoryHolder, INVENTORY_SIZE, BLANK_TITLE);

        int subCollected = countCollected(subCollection.getItems(), collected);
        ItemStack info = createSubCollectionIcon(collectionId, subCollection, subCollected);
        inventory.setItem(SLOT_INFO, info);

        int[] itemSlots = buildSlotRange(0, 44);
        for (int i = 0; i < pageSlice.pageItems().size() && i < itemSlots.length; i++) {
            String signature = pageSlice.pageItems().get(i);
            boolean isCollected = isCollectedForDisplay(collectionId, signature, collected);
            inventory.setItem(itemSlots[i], GUIHelper.createItemDisplay(signature, isCollected, subCollection.getName(), collectionId));
        }

        addNavigationButtons(inventory, pageSlice.page(), totalPages, showUncollectedOnly);

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

        if (slot == SLOT_MAIN_CLOSE && state.viewType() == ViewType.MAIN) {
            player.closeInventory();
            return;
        }

        if (slot == SLOT_BACK && state.viewType() != ViewType.MAIN) {
            handleBack(player, state);
            return;
        }

        if (slot == SLOT_FILTER && state.viewType() != ViewType.MAIN) {
            handleFilterToggle(player, state);
            return;
        }

        if (slot == SLOT_PREV && state.viewType() != ViewType.MAIN && state.hasPrevious()) {
            navigate(player, state, state.page() - 1);
            return;
        }

        if (slot == SLOT_NEXT && state.viewType() != ViewType.MAIN && state.hasNext()) {
            navigate(player, state, state.page() + 1);
            return;
        }

        SlotAction action = state.slotActions().get(slot);
        if (action == null) {
            return;
        }

        if (action.type() == ActionType.OPEN_COLLECTION) {
            openCollectionOrSubCollectionListing(player, action.collectionId(), 0, state.showUncollectedOnly());
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

        inventory.setItem(SLOT_MAIN_CLOSE, GUIHelper.createNavigationButton("Close", 24001));

        player.openInventory(inventory);
        guiStates.put(player.getUniqueId(), new GUIState(
                ViewType.MAIN,
                null,
                null,
                pageSlice.page(),
                showUncollectedOnly,
                false,
                false,
                slotActions
        ));
    }

    private void handleBack(Player player, GUIState state) {
        switch (state.viewType()) {
            case MAIN -> player.closeInventory();
            case COLLECTION -> openMainMenu(player, 0, false);
            case SUB_COLLECTION_LIST -> openMainMenu(player, 0, false);
            case SUB_COLLECTION -> openSubCollectionListingView(player, state.collectionId(), 0, state.showUncollectedOnly());
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
            case SUB_COLLECTION_LIST -> openSubCollectionListingView(player, state.collectionId(), page, showUncollectedOnly);
            case SUB_COLLECTION -> openSubCollectionView(player, state.collectionId(), state.subCollectionId(), page, showUncollectedOnly);
        }
    }

    private void openCollectionOrSubCollectionListing(Player player, String collectionId, int page, boolean showUncollectedOnly) {
        if (hasSubCollections(collectionId)) {
            openSubCollectionListingView(player, collectionId, page, showUncollectedOnly);
            return;
        }
        openCollectionView(player, collectionId, page, showUncollectedOnly);
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

    private List<String> buildDisplayItems(String collectionId, List<String> sourceItems) {
        if (!"enchantments".equals(normalizeAlias(collectionId))) {
            return new ArrayList<>(sourceItems);
        }

        List<String> expanded = new ArrayList<>();
        for (String signature : sourceItems) {
            String enchantmentKey = signature;
            String explicitLevel = null;
            if (signature.contains(":")) {
                String[] parts = signature.split(":", 2);
                enchantmentKey = parts[0].trim();
                String level = parts[1].trim();
                if (level.chars().allMatch(Character::isDigit)) {
                    explicitLevel = level;
                }
            }

            Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(enchantmentKey.toLowerCase(Locale.ROOT)));
            if (enchantment == null) {
                expanded.add(signature);
                continue;
            }

            if (explicitLevel != null) {
                expanded.add(enchantmentKey + ":" + explicitLevel);
                continue;
            }

            int maxLevel = Math.max(1, enchantment.getMaxLevel());
            for (int level = 1; level <= maxLevel; level++) {
                expanded.add(enchantmentKey + ":" + level);
            }
        }
        return expanded;
    }

    private boolean isCollectedForDisplay(String collectionId, String signature, Set<String> collectedSet) {
        if (!"enchantments".equals(normalizeAlias(collectionId))) {
            return collectedSet.contains(signature);
        }

        if (!signature.contains(":")) {
            return collectedSet.contains(signature);
        }

        String[] parts = signature.split(":", 2);
        String enchantmentKey = parts[0];
        String levelPart = parts[1];
        return collectedSet.contains(signature)
                || collectedSet.contains(enchantmentKey)
                || collectedSet.contains(enchantmentKey + "_lvl_" + levelPart)
                || collectedSet.contains((enchantmentKey + "_lvl_" + levelPart).toUpperCase(Locale.ROOT));
    }

    private boolean hasSubCollections(String collectionId) {
        CollectionDefinition collection = getCollectionById(collectionId);
        return collection != null && !collection.getSubCollections().isEmpty();
    }

    private void addNavigationButtons(Inventory inventory, int page, int totalPages, boolean filterUncollectedOnly) {
        if (page > 0) {
            ItemStack prevButton = new ItemStack(Material.PAPER);
            ItemMeta prevMeta = prevButton.getItemMeta();
            if (prevMeta != null) {
                prevMeta.setCustomModelData(100002);
                prevMeta.setDisplayName("§aPrevious Page");
                prevButton.setItemMeta(prevMeta);
            }
            inventory.setItem(SLOT_PREV, prevButton);
        }

        ItemStack backButton = new ItemStack(Material.PAPER);
        ItemMeta backMeta = backButton.getItemMeta();
        if (backMeta != null) {
            backMeta.setCustomModelData(24001);
            backMeta.setDisplayName("§cBack");
            backButton.setItemMeta(backMeta);
        }
        inventory.setItem(SLOT_BACK, backButton);

        ItemStack filterToggle = new ItemStack(filterUncollectedOnly ? Material.PAPER : Material.HOPPER);
        ItemMeta filterMeta = filterToggle.getItemMeta();
        if (filterMeta != null) {
            if (filterUncollectedOnly) {
                filterMeta.setCustomModelData(100001);
            }
            filterMeta.setDisplayName(filterUncollectedOnly
                    ? "§eShowing: §aNot Collected Only"
                    : "§eShowing: §fAll Items");
            filterMeta.setLore(List.of("§7Click to toggle item filter"));
            filterToggle.setItemMeta(filterMeta);
        }
        inventory.setItem(SLOT_FILTER, filterToggle);

        if (page < totalPages - 1) {
            ItemStack nextButton = new ItemStack(Material.PAPER);
            ItemMeta nextMeta = nextButton.getItemMeta();
            if (nextMeta != null) {
                nextMeta.setCustomModelData(100003);
                nextMeta.setDisplayName("§aNext Page");
                nextButton.setItemMeta(nextMeta);
            }
            inventory.setItem(SLOT_NEXT, nextButton);
        }
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

            if ("ores".equals(parent)) {
                return switch (sub) {
                    case "ore blocks" -> Material.DIAMOND_ORE;
                    case "ore materials" -> Material.DIAMOND;
                    default -> Material.IRON_ORE;
                };
            }

            if ("stones".equals(parent)) {
                return switch (sub) {
                    case "stone" -> Material.STONE;
                    case "cobblestone" -> Material.COBBLESTONE;
                    case "stone bricks" -> Material.STONE_BRICKS;
                    case "granite" -> Material.GRANITE;
                    case "diorite" -> Material.DIORITE;
                    case "andesite" -> Material.ANDESITE;
                    case "deepslate" -> Material.DEEPSLATE;
                    case "tuff" -> Material.TUFF;
                    case "sandstone" -> Material.SANDSTONE;
                    case "red sandstone" -> Material.RED_SANDSTONE;
                    case "bricks" -> Material.BRICKS;
                    case "resin" -> Material.RESIN_BLOCK;
                    default -> Material.STONE;
                };
            }

            if ("weapons and tools".equals(parent)) {
                return switch (sub) {
                    case "spears" -> resolveMaterial("IRON_SPEAR", Material.TRIDENT);
                    case "swords" -> Material.IRON_SWORD;
                    case "pickaxes" -> Material.IRON_PICKAXE;
                    case "axes" -> Material.IRON_AXE;
                    case "shovels" -> Material.IRON_SHOVEL;
                    case "hoes" -> Material.IRON_HOE;
                    case "other tools" -> Material.TRIDENT;
                    default -> Material.IRON_SWORD;
                };
            }

            if ("armour".equals(parent)) {
                return switch (sub) {
                    case "leather armour" -> Material.LEATHER_CHESTPLATE;
                    case "chainmail armour" -> Material.CHAINMAIL_CHESTPLATE;
                    case "copper armour" -> resolveMaterial("COPPER_CHESTPLATE", Material.IRON_CHESTPLATE);
                    case "iron armour" -> Material.IRON_CHESTPLATE;
                    case "golden armour" -> Material.GOLDEN_CHESTPLATE;
                    case "diamond armour" -> Material.DIAMOND_CHESTPLATE;
                    case "netherite armour" -> Material.NETHERITE_CHESTPLATE;
                    case "special armour" -> Material.TURTLE_HELMET;
                    default -> Material.IRON_CHESTPLATE;
                };
            }

            if ("nether".equals(parent)) {
                return switch (sub) {
                    case "nether stones" -> Material.NETHERRACK;
                    case "nether brick" -> Material.NETHER_BRICKS;
                    case "quartz" -> Material.QUARTZ;
                    case "crimson" -> Material.CRIMSON_STEM;
                    case "warped" -> Material.WARPED_STEM;
                    case "miscellaneous nether" -> Material.SOUL_SAND;
                    default -> Material.NETHERRACK;
                };
            }

            if ("nature".equals(parent)) {
                return switch (sub) {
                    case "mud and dirt" -> Material.DIRT;
                    case "flowers" -> Material.POPPY;
                    case "foliage" -> Material.OAK_LEAVES;
                    case "mushrooms" -> Material.RED_MUSHROOM;
                    case "ground materials" -> Material.GRAVEL;
                    case "ice and snow" -> Material.ICE;
                    case "bees" -> Material.HONEYCOMB_BLOCK;
                    case "pumpkins" -> Material.PUMPKIN;
                    case "miscellaneous nature" -> Material.HAY_BLOCK;
                    default -> Material.GRASS_BLOCK;
                };
            }
        }

        return switch (normalized) {
            case "wood" -> Material.OAK_LOG;
            case "ores" -> Material.IRON_ORE;
            case "stones" -> Material.STONE;
            case "weapons and tools" -> Material.IRON_SWORD;
            case "nature" -> Material.GRASS_BLOCK;
            case "sniffer" -> Material.SNIFFER_EGG;
            case "nether" -> Material.NETHERRACK;
            case "potion ingredients" -> Material.GHAST_TEAR;
            case "utilities" -> Material.CRAFTING_TABLE;
            case "enchantment books", "enchantments", "enchants" -> Material.ENCHANTED_BOOK;
            case "ancient cities" -> Material.SCULK;
            case "end" -> Material.END_STONE;
            case "shulker" -> Material.SHULKER_BOX;
            case "food" -> Material.COOKED_BEEF;
            case "redstone" -> Material.REDSTONE;
            case "ocean" -> Material.TUBE_CORAL;
            case "candles" -> Material.CANDLE;
            case "mob drops" -> Material.ROTTEN_FLESH;
            case "sherds" -> Material.ANGLER_POTTERY_SHERD;
            case "copper" -> Material.COPPER_BLOCK;
            case "heads", "mob heads" -> Material.ZOMBIE_HEAD;
            case "trial chambers" -> Material.TRIAL_KEY;
            case "concrete" -> Material.GRAY_CONCRETE;
            case "wool" -> Material.WHITE_WOOL;
            case "glass" -> Material.GLASS;
            case "terracotta" -> Material.TERRACOTTA;
            case "armour" -> Material.IRON_CHESTPLATE;
            case "dyes" -> Material.RED_DYE;
            case "banners" -> Material.YELLOW_BANNER;
            case "beds" -> Material.RED_BED;
            case "music discs" -> Material.MUSIC_DISC_CHIRP;
            case "buckets" -> Material.WATER_BUCKET;
            case "fishing", "jb fishing +", "jb fishing plus", "custom fish" -> Material.FISHING_ROD;
            case "potions" -> Material.POTION;
            case "armour trims", "armor trims" -> Material.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE;
            case "horns" -> Material.GOAT_HORN;
            case "tcg" -> Material.PAPER;
            case "spawn eggs" -> Material.ZOMBIE_SPAWN_EGG;
            default -> Material.PAPER;
        };
    }

    private Material resolveMaterial(String materialName, Material fallback) {
        Material resolved = Material.matchMaterial(materialName);
        return resolved != null ? resolved : fallback;
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
        SUB_COLLECTION_LIST,
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
            throw new UnsupportedOperationException("Collection GUI holder does not expose a root inventory");
        }
    }
}
