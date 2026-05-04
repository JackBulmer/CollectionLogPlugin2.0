package collectionlog.milestones;

import com.google.gson.JsonObject;
import collectionlog.CollectionLogPlugin;
import collectionlog.crafting.CustomRecipeManager;
import collectionlog.database.DatabaseType;
import collectionlog.tags.Tag;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class MilestoneManager {
    private final CollectionLogPlugin plugin;
    private final Map<String, Milestone> milestones;
    private final Map<UUID, Set<String>> playerUnlockedPerks;
    private final Map<UUID, Set<String>> playerCompletedMilestones;
    private final Set<UUID> loadedPerks;
    private final Set<UUID> loadedCompletedMilestones;
    private final ConcurrentMap<UUID, Object> playerLoadLocks;
    private List<String> globalPrefixTags;
    private List<String> globalSuffixTags;

    public MilestoneManager(CollectionLogPlugin plugin) {
        this.plugin = plugin;
        this.milestones = new LinkedHashMap<>();
        this.playerUnlockedPerks = new ConcurrentHashMap<>();
        this.playerCompletedMilestones = new ConcurrentHashMap<>();
        this.loadedPerks = ConcurrentHashMap.newKeySet();
        this.loadedCompletedMilestones = ConcurrentHashMap.newKeySet();
        this.playerLoadLocks = new ConcurrentHashMap<>();
        this.globalPrefixTags = new ArrayList<>();
        this.globalSuffixTags = new ArrayList<>();

        loadMilestones();
        validateMilestones();
    }

    private void loadMilestones() {
        File milestonesFile = new File(plugin.getDataFolder(), "milestones.yml");
        if (!milestonesFile.exists()) {
            plugin.saveResource("milestones.yml", false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(milestonesFile);

        // Load global (always-available) tags
        globalPrefixTags = new ArrayList<>(config.getStringList("global_tags.prefix"));
        globalSuffixTags = new ArrayList<>(config.getStringList("global_tags.suffix"));

        ConfigurationSection collectionSection = config.getConfigurationSection("collections");
        if (collectionSection != null) {
            for (String collectionName : collectionSection.getKeys(false)) {
                String path = "collections." + collectionName;

                List<String> perks = config.getStringList(path + ".perks");
                List<String> prefixTags = config.getStringList(path + ".tags.prefix");
                List<String> suffixTags = config.getStringList(path + ".tags.suffix");
                String displayName = capitalizeCollectionName(collectionName);
                String toastMessage = config.getString(path + ".toast_message",
                        displayName + " Collection Complete!");
                String announcementMessage = config.getString(
                        path + ".announcement_message",
                        "{player} has completed the " + displayName + " Collection!"
                );

                Milestone milestone = new Milestone(
                        collectionName,
                        MilestoneType.COLLECTION,
                        perks,
                        prefixTags,
                        suffixTags,
                        toastMessage,
                        announcementMessage
                );

                milestones.put(normalizeMilestoneKey(collectionName), milestone);
            }
        }

        ConfigurationSection subCollectionsSection = config.getConfigurationSection("sub_collections");
        if (subCollectionsSection != null) {
            for (String parentCollection : subCollectionsSection.getKeys(false)) {
                ConfigurationSection parentSection = subCollectionsSection.getConfigurationSection(parentCollection);
                if (parentSection == null) {
                    continue;
                }

                for (String subCollection : parentSection.getKeys(false)) {
                    String fullName = parentCollection + ":" + subCollection;
                    String path = "sub_collections." + parentCollection + "." + subCollection;

                    int xpReward = config.getInt(path + ".xp", 0);
                    List<String> itemRewards = config.getStringList(path + ".items");
                    List<String> subPerks = config.getStringList(path + ".perks");
                    String displayName = capitalizeCollectionName(subCollection);
                    String toastMessage = config.getString(path + ".toast_message",
                            displayName + " Complete!");

                    SubCollectionMilestone milestone = new SubCollectionMilestone(
                            fullName,
                            parentCollection,
                            subCollection,
                            xpReward,
                            itemRewards,
                            subPerks,
                            toastMessage
                    );

                    milestones.put(normalizeMilestoneKey(fullName), milestone);
                }
            }
        }

        plugin.getLogger().info("Loaded " + milestones.size() + " milestones");
    }

    /**
     * Validates that every collection defined in collections.json has a corresponding
     * milestone in milestones.yml, and warns about any mismatches on startup.
     */
    private void validateMilestones() {
        if (plugin.getCollectionLoader() == null) {
            return;
        }

        Set<String> collectionIds = new HashSet<>();
        try {
            collectionIds.addAll(plugin.getCollectionLoader().getCollections().keySet());
            if (plugin.getSpecialCollectionLoader() != null) {
                collectionIds.addAll(plugin.getSpecialCollectionLoader().getSpecialCollections().keySet());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Milestone Validation] Could not read collection definitions: " + e.getMessage());
            return;
        }

        int missingMilestones = 0;
        for (String collectionId : collectionIds) {
            if (!milestones.containsKey(normalizeMilestoneKey(collectionId))) {
                plugin.getLogger().warning("[Milestone Validation] Collection '" + collectionId
                        + "' has no entry in milestones.yml – completion messages will not be sent.");
                missingMilestones++;
            }
        }

        int orphanMilestones = 0;
        for (Map.Entry<String, Milestone> entry : milestones.entrySet()) {
            if (entry.getValue() instanceof SubCollectionMilestone) {
                continue;
            }
            if (!collectionIds.contains(entry.getKey())) {
                plugin.getLogger().warning("[Milestone Validation] milestones.yml entry '" + entry.getKey()
                        + "' has no corresponding collection in collections.json.");
                orphanMilestones++;
            }
        }

        if (missingMilestones == 0 && orphanMilestones == 0) {
            plugin.getLogger().info("[Milestone Validation] All collections and milestones match. ✓");
        }
    }

    /**
     * Returns all Tag objects built from milestones.yml (collection-specific and global).
     * This is the single source of truth for all available tags.
     */
    public List<Tag> getAllTags() {
        List<Tag> tags = new ArrayList<>();

        // Global tags (no collection requirement)
        for (String displayName : globalPrefixTags) {
            tags.add(new Tag(displayName, true, null));
        }
        for (String displayName : globalSuffixTags) {
            tags.add(new Tag(displayName, false, null));
        }

        // Collection-specific tags
        for (Map.Entry<String, Milestone> entry : milestones.entrySet()) {
            Milestone milestone = entry.getValue();
            if (milestone instanceof SubCollectionMilestone) {
                continue;
            }
            String collectionName = entry.getKey();
            for (String displayName : milestone.getPrefixTags()) {
                tags.add(new Tag(displayName, true, collectionName));
            }
            for (String displayName : milestone.getSuffixTags()) {
                tags.add(new Tag(displayName, false, collectionName));
            }
        }

        return tags;
    }

    public void checkCollectionCompletion(UUID playerId, String collectionName) {
        triggerMilestone(playerId, collectionName);
    }

    public void checkSubCollectionCompletion(UUID playerId, String parentCollection, String subCollection) {
        triggerMilestone(playerId, parentCollection + ":" + subCollection);
    }

    private void triggerMilestone(UUID playerId, String milestoneName) {
        String normalizedKey = normalizeMilestoneKey(milestoneName);
        Milestone milestone = milestones.get(normalizedKey);
        if (milestone == null) {
            plugin.getLogger().warning("No milestone found for: '" + milestoneName + "' (normalized: '" + normalizedKey + "')");
            plugin.getLogger().warning("Available milestones: " + milestones.keySet());
            return;
        }
        if (hasCompletedMilestone(playerId, milestoneName)) {
            return;
        }

        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null) {
            return;
        }

        markMilestoneCompleted(playerId, milestoneName);

        if (milestone instanceof SubCollectionMilestone subMilestone) {
            awardSubCollectionRewards(player, subMilestone);
        } else {
            awardCollectionRewards(player, milestone);
        }

        showMilestoneNotifications(player, milestone);
    }

    private void awardCollectionRewards(Player player, Milestone milestone) {
        List<String> unlockedRewards = new ArrayList<>();
        boolean hasExtraHeart = false;

        List<String> perks = milestone.getPerks();
        if (perks != null && !perks.isEmpty()) {
            for (String perkId : perks) {
                unlockPerk(player.getUniqueId(), perkId);
                unlockedRewards.add("§6Perk: §e" + formatDisplayName(perkId));

                if ("extra_heart".equals(perkId)) {
                    hasExtraHeart = true;
                }

                // Special handling for infinite_lava_bucket - give the physical item
                if ("infinite_lava_bucket".equals(perkId) && plugin.getInfiniteLavaBucket() != null) {
                    player.getInventory().addItem(plugin.getInfiniteLavaBucket().createItem());
                    player.sendMessage("§6✦ §eYou received: §6§lInfinite Lava Bucket");
                }
            }
            // Apply any newly-unlocked potion-effect perks immediately
            plugin.getPotionEffectManager().applyPermanentEffects(player);
            // Apply extra hearts immediately if the extra_heart perk was awarded
            if (hasExtraHeart) {
                plugin.getExtraHeartsManager().applyExtraHearts(player);
            }
        }

        for (String tagDisplayName : milestone.getPrefixTags()) {
            unlockTag(player.getUniqueId(), tagDisplayName);
            unlockedRewards.add("§6Tag: §r" + tagDisplayName);
        }

        for (String tagDisplayName : milestone.getSuffixTags()) {
            unlockTag(player.getUniqueId(), tagDisplayName);
            unlockedRewards.add("§6Tag: §r" + tagDisplayName);
        }

        if (!unlockedRewards.isEmpty()) {
            player.sendMessage("§a§l✦ Collection Rewards ✦");
            for (String reward : unlockedRewards) {
                player.sendMessage("  " + reward);
            }
        }

        // Discover any custom recipes unlocked by this collection
        if (plugin.getCustomRecipeManager() != null) {
            plugin.getCustomRecipeManager().discoverRecipesForPlayer(player);
        }
    }

    private void awardSubCollectionRewards(Player player, SubCollectionMilestone milestone) {
        if (milestone.getXpReward() > 0) {
            player.giveExp(milestone.getXpReward());
            player.sendMessage("§a+ " + milestone.getXpReward() + " XP");
        }

        List<String> perks = milestone.getPerks();
        if (!perks.isEmpty()) {
            List<String> unlockedRewards = new ArrayList<>();
            boolean hasExtraHeart = false;
            for (String perkId : perks) {
                unlockPerk(player.getUniqueId(), perkId);
                unlockedRewards.add("§6Perk: §e" + formatDisplayName(perkId));
                if ("extra_heart".equals(perkId)) {
                    hasExtraHeart = true;
                }
            }
            // Apply any newly-unlocked potion-effect perks immediately
            plugin.getPotionEffectManager().applyPermanentEffects(player);
            // Apply extra hearts immediately if the extra_heart perk was awarded
            if (hasExtraHeart) {
                plugin.getExtraHeartsManager().applyExtraHearts(player);
            }
            if (!unlockedRewards.isEmpty()) {
                player.sendMessage("§a§l✦ Sub-Collection Rewards ✦");
                for (String reward : unlockedRewards) {
                    player.sendMessage("  " + reward);
                }
            }
        }

        // Discover any custom recipes unlocked by this sub-collection
        if (plugin.getCustomRecipeManager() != null) {
            plugin.getCustomRecipeManager().discoverRecipesForPlayer(player);
        }
    }

    private void showMilestoneNotifications(Player player, Milestone milestone) {
        if (milestone.getToastMessage() != null && !milestone.getToastMessage().isBlank()) {
            showToastNotification(player, milestone.getToastMessage(), milestone.getType(), milestone);
        }

        if (milestone.getType() == MilestoneType.COLLECTION) {
            String announcementTemplate = milestone.getAnnouncementMessage();
            if (announcementTemplate == null || announcementTemplate.isBlank()) {
                announcementTemplate = "{player} has completed the {collection} collection!";
            }
            String announcement = announcementTemplate
                    .replace("{player}", player.getName())
                    .replace("{collection}", milestone.getName());
            plugin.getServer().broadcastMessage("§6§l✦ " + announcement);
        }

        plugin.getLogger().info(player.getName() + " completed milestone: " + milestone.getName());
    }

    private void showToastNotification(Player player, String message, MilestoneType type, Milestone milestone) {
        final NamespacedKey key = new NamespacedKey(plugin, "toast_" + UUID.randomUUID().toString().replace("-", ""));
        try {
            Material icon = getIconForMilestone(milestone, type);
            String frame = type == MilestoneType.COLLECTION ? "challenge" : "task";

            JsonObject json = new JsonObject();

            JsonObject display = new JsonObject();
            JsonObject displayIcon = new JsonObject();
            displayIcon.addProperty("id", icon.getKey().toString());
            display.add("icon", displayIcon);

            JsonObject title = new JsonObject();
            title.addProperty("text", message);
            display.add("title", title);

            JsonObject description = new JsonObject();
            description.addProperty("text", "");
            display.add("description", description);

            display.addProperty("frame", frame);
            display.addProperty("show_toast", true);
            display.addProperty("announce_to_chat", false);
            display.addProperty("hidden", true);
            json.add("display", display);

            JsonObject criteria = new JsonObject();
            JsonObject impossible = new JsonObject();
            impossible.addProperty("trigger", "minecraft:impossible");
            criteria.add("impossible", impossible);
            json.add("criteria", criteria);

            Advancement advancement = Bukkit.getUnsafe().loadAdvancement(key, json.toString());
            if (advancement == null) {
                Bukkit.getUnsafe().removeAdvancement(key);
                player.sendMessage("§6§l✦ " + message);
                return;
            }

            AdvancementProgress progress = player.getAdvancementProgress(advancement);
            Set<String> remainingCriteria = new HashSet<>(progress.getRemainingCriteria());
            for (String criterion : remainingCriteria) {
                progress.awardCriteria(criterion);
            }

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    if (player.isOnline()) {
                        AdvancementProgress updatedProgress = player.getAdvancementProgress(advancement);
                        Set<String> awardedCriteria = new HashSet<>(updatedProgress.getAwardedCriteria());
                        for (String criterion : awardedCriteria) {
                            updatedProgress.revokeCriteria(criterion);
                        }
                    }
                } finally {
                    Bukkit.getUnsafe().removeAdvancement(key);
                }
            }, 20L);
        } catch (Exception exception) {
            plugin.getLogger().warning("Failed to show toast notification: " + exception.getMessage());
            Bukkit.getUnsafe().removeAdvancement(key);
            player.sendMessage("§6§l✦ " + message);
        }
    }

    private Material getIconForMilestone(Milestone milestone, MilestoneType type) {
        Material defaultIcon = type == MilestoneType.COLLECTION ? Material.DIAMOND : Material.EXPERIENCE_BOTTLE;
        if (milestone == null) {
            return defaultIcon;
        }

        String collectionName;
        if (milestone instanceof SubCollectionMilestone subMilestone) {
            collectionName = subMilestone.getParentCollection() + ":" + subMilestone.getSubCollection();
        } else {
            collectionName = milestone.getName();
        }

        Material icon = plugin.getCollectionGUI().getMaterialForCollection(collectionName);
        if (icon == null || icon == Material.PAPER) {
            return defaultIcon;
        }
        return icon;
    }

    public void unlockPerk(UUID playerId, String perkId) {
        ensurePerksLoaded(playerId);
        playerUnlockedPerks.computeIfAbsent(playerId, ignored -> ConcurrentHashMap.newKeySet()).add(perkId);
        savePerkToDatabase(playerId, perkId);
        // Ensure toggle state is loaded so perk checks work immediately without a relog
        plugin.getPerkManager().loadPlayerToggles(playerId);

        // Apply extra heart if this perk is extra_heart
        if ("extra_heart".equals(perkId)) {
            plugin.getExtraHeartsManager().updateExtraHearts(playerId);
        }
    }

    public boolean hasPerk(UUID playerId, String perkId) {
        ensurePerksLoaded(playerId);
        return playerUnlockedPerks.getOrDefault(playerId, Collections.emptySet()).contains(perkId);
    }

    public Set<String> getPlayerPerks(UUID playerId) {
        ensurePerksLoaded(playerId);
        return new HashSet<>(playerUnlockedPerks.getOrDefault(playerId, Collections.emptySet()));
    }

    /**
     * Counts how many milestones that award the {@code extra_heart} perk
     * the given player has completed. Used by {@link collectionlog.perks.ExtraHeartsManager}
     * to determine the correct number of bonus health points to apply.
     */
    public int countExtraHeartMilestones(UUID playerId) {
        ensureCompletedMilestonesLoaded(playerId);
        Set<String> completed = playerCompletedMilestones.getOrDefault(playerId, Collections.emptySet());
        return (int) milestones.values().stream()
                .filter(m -> m.getPerks().contains("extra_heart"))
                .filter(m -> completed.contains(normalizeMilestoneKey(m.getName())))
                .count();
    }

    /**
     * Removes a single perk from the player's unlocked set and deletes it
     * from the database. Used by {@link collectionlog.perks.PerkManager} for
     * admin-triggered individual revokes.
     */
    public void revokePerk(UUID playerId, String perkId) {
        ensurePerksLoaded(playerId);
        Set<String> perks = playerUnlockedPerks.computeIfAbsent(playerId, ignored -> ConcurrentHashMap.newKeySet());
        perks.remove(perkId);
        removePerkFromDatabase(playerId, perkId);
    }

    private void unlockTag(UUID playerId, String tagId) {
        plugin.getAsyncTaskManager().runAsync(() -> {
            String sql = insertIgnore("player_tags", "(player_uuid, tag_id, unlocked_at) VALUES (?, ?, ?)");
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerId.toString());
                stmt.setString(2, tagId);
                stmt.setLong(3, System.currentTimeMillis());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to unlock tag " + tagId + " for " + playerId, e);
            }
        });
    }

    public boolean hasCompletedMilestone(UUID playerId, String milestoneName) {
        ensureCompletedMilestonesLoaded(playerId);
        String normalizedMilestone = normalizeMilestoneKey(milestoneName);
        return playerCompletedMilestones
                .getOrDefault(playerId, Collections.emptySet())
                .contains(normalizedMilestone);
    }

    private void markMilestoneCompleted(UUID playerId, String milestoneName) {
        String normalizedMilestone = normalizeMilestoneKey(milestoneName);
        playerCompletedMilestones
                .computeIfAbsent(playerId, ignored -> ConcurrentHashMap.newKeySet())
                .add(normalizedMilestone);

        loadedCompletedMilestones.add(playerId);

        plugin.getAsyncTaskManager().runAsync(() -> {
            String sql = insertIgnore(
                    "completed_milestones",
                    "(player_uuid, milestone_name, completed_at) VALUES (?, ?, ?)"
            );

            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerId.toString());
                stmt.setString(2, normalizedMilestone);
                stmt.setLong(3, System.currentTimeMillis());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to mark milestone complete: " + normalizedMilestone, e);
            }
        });
    }

    private void savePerkToDatabase(UUID playerId, String perkId) {
        plugin.getAsyncTaskManager().runAsync(() -> {
            String sql = insertIgnore("player_perks", "(player_uuid, perk_id, unlocked_at) VALUES (?, ?, ?)");
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerId.toString());
                stmt.setString(2, perkId);
                stmt.setLong(3, System.currentTimeMillis());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save perk " + perkId + " for " + playerId, e);
            }
        });
    }

    public void loadPlayerPerks(UUID playerId) {
        ensurePerksLoaded(playerId);
    }

    public void revokeCollectionMilestone(UUID playerId, String collectionName) {
        Milestone milestone = milestones.get(normalizeMilestoneKey(collectionName));
        if (milestone == null) {
            return;
        }

        revokeMilestoneRewards(playerId, milestone);
        removeCompletedMilestone(playerId, collectionName);

        // Update extra hearts if this milestone awarded the extra_heart perk
        if (milestone.getPerks().contains("extra_heart")) {
            plugin.getExtraHeartsManager().updateExtraHearts(playerId);
        }

        // Undiscover any custom recipes that were locked behind this collection
        if (plugin.getCustomRecipeManager() != null) {
            Player onlinePlayer = plugin.getServer().getPlayer(playerId);
            if (onlinePlayer != null) {
                plugin.getCustomRecipeManager().undiscoverRecipesForPlayer(onlinePlayer, collectionName);
            }
        }
    }

    public void revokeCollectionMilestones(UUID playerId, String collectionName) {
        revokeCollectionMilestone(playerId, collectionName);

        ensureCompletedMilestonesLoaded(playerId);
        String prefix = normalizeMilestoneKey(collectionName) + ":";
        Set<String> completed = playerCompletedMilestones.get(playerId);
        if (completed == null || completed.isEmpty()) {
            return;
        }

        List<String> toRemove = new ArrayList<>();
        for (String milestoneName : completed) {
            if (milestoneName.startsWith(prefix)) {
                toRemove.add(milestoneName);
            }
        }

        if (toRemove.isEmpty()) {
            return;
        }

        completed.removeAll(toRemove);
        boolean removedExtraHeart = false;
        for (String milestoneName : toRemove) {
            // Also revoke any perks granted by the sub-collection milestone
            Milestone subMilestone = milestones.get(milestoneName);
            if (subMilestone != null) {
                revokeMilestoneRewards(playerId, subMilestone);
                // Track if any extra_heart perks were removed
                if (subMilestone.getPerks().contains("extra_heart")) {
                    removedExtraHeart = true;
                }
            }
            removeCompletedMilestoneFromDatabase(playerId, milestoneName);
        }

        // Update extra hearts if any were removed
        if (removedExtraHeart) {
            plugin.getExtraHeartsManager().updateExtraHearts(playerId);
        }
    }

    public void reload() {
        milestones.clear();
        globalPrefixTags = new ArrayList<>();
        globalSuffixTags = new ArrayList<>();
        loadMilestones();
        validateMilestones();
    }

    private void revokeMilestoneRewards(UUID playerId, Milestone milestone) {
        ensurePerksLoaded(playerId);
        Set<String> perks = playerUnlockedPerks.computeIfAbsent(playerId, ignored -> ConcurrentHashMap.newKeySet());
        for (String perkId : milestone.getPerks()) {
            perks.remove(perkId);
            removePerkFromDatabase(playerId, perkId);
        }

        for (String tagDisplayName : milestone.getPrefixTags()) {
            removeTagFromDatabase(playerId, tagDisplayName);
        }
        for (String tagDisplayName : milestone.getSuffixTags()) {
            removeTagFromDatabase(playerId, tagDisplayName);
        }
    }

    private void removeCompletedMilestone(UUID playerId, String milestoneName) {
        ensureCompletedMilestonesLoaded(playerId);
        String normalizedMilestone = normalizeMilestoneKey(milestoneName);
        playerCompletedMilestones
                .computeIfAbsent(playerId, ignored -> ConcurrentHashMap.newKeySet())
                .remove(normalizedMilestone);
        removeCompletedMilestoneFromDatabase(playerId, normalizedMilestone);
    }

    private void removePerkFromDatabase(UUID playerId, String perkId) {
        plugin.getAsyncTaskManager().runAsync(() -> {
            String sql = "DELETE FROM player_perks WHERE player_uuid = ? AND perk_id = ?";
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerId.toString());
                stmt.setString(2, perkId);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to remove perk " + perkId + " for " + playerId, e);
            }
        });
    }

    private void removeTagFromDatabase(UUID playerId, String tagId) {
        plugin.getAsyncTaskManager().runAsync(() -> {
            String sql = "DELETE FROM player_tags WHERE player_uuid = ? AND tag_id = ?";
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerId.toString());
                stmt.setString(2, tagId);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to remove tag " + tagId + " for " + playerId, e);
            }
        });
    }

    private void removeCompletedMilestoneFromDatabase(UUID playerId, String milestoneName) {
        plugin.getAsyncTaskManager().runAsync(() -> {
            String sql = "DELETE FROM completed_milestones WHERE player_uuid = ? AND milestone_name = ?";
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerId.toString());
                stmt.setString(2, normalizeMilestoneKey(milestoneName));
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to remove completed milestone " + milestoneName + " for " + playerId, e);
            }
        });
    }

    private void ensurePerksLoaded(UUID playerId) {
        if (loadedPerks.contains(playerId)) {
            return;
        }
        synchronized (getPerPlayerLock(playerId)) {
            if (loadedPerks.contains(playerId)) {
                return;
            }

            Set<String> loaded = new HashSet<>();
            String sql = "SELECT perk_id FROM player_perks WHERE player_uuid = ?";
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerId.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        loaded.add(rs.getString("perk_id"));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load player perks for " + playerId, e);
            }

            Set<String> perks = ConcurrentHashMap.newKeySet();
            perks.addAll(loaded);
            playerUnlockedPerks.put(playerId, perks);
            loadedPerks.add(playerId);
        }
    }

    private void ensureCompletedMilestonesLoaded(UUID playerId) {
        if (loadedCompletedMilestones.contains(playerId)) {
            return;
        }
        synchronized (getPerPlayerLock(playerId)) {
            if (loadedCompletedMilestones.contains(playerId)) {
                return;
            }

            Set<String> completed = new HashSet<>();
            String sql = "SELECT milestone_name FROM completed_milestones WHERE player_uuid = ?";
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerId.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        completed.add(normalizeMilestoneKey(rs.getString("milestone_name")));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load completed milestones for " + playerId, e);
            }

            Set<String> completedMilestones = ConcurrentHashMap.newKeySet();
            completedMilestones.addAll(completed);
            playerCompletedMilestones.put(playerId, completedMilestones);
            loadedCompletedMilestones.add(playerId);
        }
    }

    private Object getPerPlayerLock(UUID playerId) {
        return playerLoadLocks.computeIfAbsent(playerId, ignored -> new Object());
    }

    private String insertIgnore(String table, String columnsAndValues) {
        DatabaseType type = plugin.getDatabaseManager().getDatabaseType();
        if (type == DatabaseType.MYSQL) {
            return "INSERT IGNORE INTO " + table + " " + columnsAndValues;
        }
        return "INSERT OR IGNORE INTO " + table + " " + columnsAndValues;
    }

    public String normalizeMilestoneKey(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Get all completed milestone keys for a player.
     * Used by admin commands to find completed collections.
     */
    public Set<String> getPlayerCompletedMilestones(UUID playerId) {
        ensureCompletedMilestonesLoaded(playerId);
        return playerCompletedMilestones.getOrDefault(playerId, Collections.emptySet());
    }

    /**
     * Get all registered milestones.
     * Used by admin commands to search for specific perk rewards.
     */
    public java.util.Collection<Milestone> getAllMilestones() {
        return milestones.values();
    }

    private String formatDisplayName(String id) {
        String[] words = id.replace("_", " ").toLowerCase(Locale.ROOT).split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                builder.append(word.substring(1));
            }
        }
        return builder.toString();
    }

    private String capitalizeCollectionName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        String[] words = name.trim().toLowerCase(Locale.ROOT).split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                result.append(word.substring(1));
            }
        }
        return result.toString();
    }
}
