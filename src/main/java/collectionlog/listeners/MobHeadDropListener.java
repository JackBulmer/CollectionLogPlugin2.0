package collectionlog.listeners;

import collectionlog.CollectionLogPlugin;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import me.arcaniax.hdb.api.HeadDatabaseAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.logging.Level;

/**
 * Handles mob head and spawn egg drops when mobs are killed by players.
 */
public class MobHeadDropListener implements Listener {
    private final CollectionLogPlugin plugin;
    private final Random random;
    private final HeadDatabaseAPI hdbAPI;
    private final NamespacedKey mobHeadKey;
    private final NamespacedKey spawnEggKey;
    private final NamespacedKey mythicMobHeadKey;
    private final NamespacedKey mythicMobHeadIdKey;
    private final Map<String, String> customMobHeads;

    public MobHeadDropListener(CollectionLogPlugin plugin) {
        this.plugin = plugin;
        this.random = new Random();
        this.hdbAPI = new HeadDatabaseAPI();
        this.mobHeadKey = new NamespacedKey(plugin, "mob_head");
        this.spawnEggKey = new NamespacedKey(plugin, "spawn_egg");
        this.mythicMobHeadKey = new NamespacedKey(plugin, "mythic_mob_head");
        this.mythicMobHeadIdKey = new NamespacedKey(plugin, "mythic_mob_head_id");
        this.customMobHeads = new HashMap<>();
        loadCustomMobHeads();
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onMobDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();

        if (!(entity instanceof LivingEntity livingEntity)) {
            return;
        }

        Player killer = livingEntity.getKiller();
        if (killer == null) {
            return;
        }

        int spawnEggChance = plugin.getConfig().getInt("mob-drops.spawn-egg-chance", 10);
        int mobHeadChance = plugin.getConfig().getInt("mob-drops.mob-head-chance", 7);

        boolean spawnEggsEnabled = plugin.getConfig().getBoolean("mob-drops.spawn-eggs-enabled", true);
        boolean mobHeadsEnabled = plugin.getConfig().getBoolean("mob-drops.mob-heads-enabled", true);

        Optional<String> mythicMobInternalName = isMythicMob(entity);
        if (mythicMobInternalName.isPresent()) {
            if (mobHeadsEnabled && random.nextInt(mobHeadChance) == 0) {
                ItemStack customMobHead = getCustomMobHead(mythicMobInternalName.get());
                if (customMobHead != null) {
                    dropGlowingItem(entity.getWorld(), entity.getLocation(), customMobHead);
                    spawnParticleEffect(entity.getLocation());
                    plugin.getLogger().info("Mythic mob head dropped: " + mythicMobInternalName.get());
                }
            }
            return;
        }

        // Spawn egg drop (1 in X chance)
        if (spawnEggsEnabled && random.nextInt(spawnEggChance) == 0) {
            ItemStack spawnEgg = getSpawnEgg(entity.getType());
            if (spawnEgg != null) {
                ItemMeta meta = spawnEgg.getItemMeta();
                if (meta != null) {
                    meta.getPersistentDataContainer().set(spawnEggKey, PersistentDataType.STRING, entity.getType().name());
                    spawnEgg.setItemMeta(meta);
                }
                dropGlowingItem(entity.getWorld(), entity.getLocation(), spawnEgg);
                spawnParticleEffect(entity.getLocation());
                plugin.getLogger().info("Spawn Egg Dropped: " + spawnEgg.getType() + " for " + entity.getType());
            }
        }

        // Mob head drop (1 in X chance, Ender Dragon excluded)
        if (mobHeadsEnabled && entity.getType() != EntityType.ENDER_DRAGON && random.nextInt(mobHeadChance) == 0) {
            ItemStack mobHead = getMobHead(entity.getType());
            if (mobHead != null) {
                dropGlowingItem(entity.getWorld(), entity.getLocation(), mobHead);
                spawnParticleEffect(entity.getLocation());
                plugin.getLogger().info("Mob Head Dropped: " + entity.getType() + " head");
            }
        }
    }

    private void spawnParticleEffect(Location location) {
        World world = location.getWorld();
        if (world == null) return;
        world.spawnParticle(Particle.HAPPY_VILLAGER, location, 12, 0.4, 0.4, 0.4, 0.01);
        world.spawnParticle(Particle.END_ROD, location, 20, 0.25, 0.35, 0.25, 0.02);
        world.spawnParticle(Particle.FIREWORK, location, 18, 0.3, 0.3, 0.3, 0.08);
        world.spawnParticle(Particle.ENCHANT, location, 8, 0.5, 0.5, 0.5, 0.2);
        world.playSound(location, Sound.ENTITY_PLAYER_LEVELUP, 2.0f, 1.15f);
    }

    private void dropGlowingItem(World world, Location location, ItemStack itemStack) {
        Item item = world.dropItemNaturally(location, itemStack);
        item.setGlowing(true);

        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam("greenGlow");
        if (team == null) {
            team = scoreboard.registerNewTeam("greenGlow");
            team.setColor(ChatColor.GREEN);
        }
        team.addEntry(item.getUniqueId().toString());
    }

    private ItemStack getMobHead(EntityType entityType) {
        String headID = MOB_HEAD_IDS.get(entityType);
        if (headID == null) {
            return null;
        }

        // Vanilla in-game heads
        if (headID.endsWith("_HEAD") || headID.endsWith("_SKULL")) {
            Material headMaterial = Material.getMaterial(headID);
            if (headMaterial != null) {
                ItemStack head = new ItemStack(headMaterial);
                ItemMeta meta = head.getItemMeta();
                if (meta != null) {
                    meta.getPersistentDataContainer().set(mobHeadKey, PersistentDataType.STRING, entityType.name() + "_HEAD");
                    head.setItemMeta(meta);
                }
                return head;
            }
            return null;
        }

        // Custom heads from HeadDatabase
        ItemStack mobHead = hdbAPI.getItemHead(headID);
        if (mobHead != null) {
            ItemMeta meta = mobHead.getItemMeta();
            if (meta != null) {
                String entityName = capitalizeWords(entityType.name().replace("_", " "));
                meta.setDisplayName(ChatColor.GOLD + entityName + " Head");
                PersistentDataContainer dataContainer = meta.getPersistentDataContainer();
                dataContainer.set(mobHeadKey, PersistentDataType.STRING, entityType.name() + "_HEAD");
                mobHead.setItemMeta(meta);
            }
        }
        return mobHead;
    }

    private ItemStack getSpawnEgg(EntityType entityType) {
        Material eggMaterial = SPAWN_EGG_MATERIALS.get(entityType);
        return eggMaterial != null ? new ItemStack(eggMaterial) : null;
    }

    private Optional<String> isMythicMob(Entity entity) {
        if (!plugin.isMythicMobsAvailable()) {
            return Optional.empty();
        }

        try {
            Optional<ActiveMob> mythicMob = MythicBukkit.inst().getMobManager().getActiveMob(entity.getUniqueId());
            return mythicMob.map(activeMob -> activeMob.getType().getInternalName());
        } catch (Exception e) {
            if (plugin.getConfig().getBoolean("plugin.debug", false)) {
                plugin.getLogger().log(Level.WARNING, "Failed to check MythicMobs mob status", e);
            }
            return Optional.empty();
        }
    }

    private ItemStack getCustomMobHead(String internalName) {
        if (internalName == null || internalName.isBlank()) {
            return null;
        }

        String headId = customMobHeads.get(internalName.toLowerCase(Locale.ROOT));
        if (headId == null || headId.isBlank()) {
            return null;
        }

        ItemStack mobHead = hdbAPI.getItemHead(headId);
        if (mobHead == null) {
            plugin.getLogger().warning("Invalid HeadDatabase ID '" + headId + "' for MythicMobs mob '" + internalName + "'");
            return null;
        }

        ItemMeta meta = mobHead.getItemMeta();
        if (meta != null) {
            String displayName = MYTHIC_HEAD_DISPLAY_NAMES.getOrDefault(headId, capitalizeWords(internalName.replace('_', ' ')) + " Head");
            meta.setDisplayName(ChatColor.GOLD + displayName);
            PersistentDataContainer dataContainer = meta.getPersistentDataContainer();
            dataContainer.set(mythicMobHeadKey, PersistentDataType.STRING, internalName);
            dataContainer.set(mythicMobHeadIdKey, PersistentDataType.STRING, headId);
            mobHead.setItemMeta(meta);
        }

        return mobHead;
    }

    private void loadCustomMobHeads() {
        customMobHeads.clear();

        File configFile = new File(plugin.getDataFolder(), "custom_mob_heads.yml");
        if (!configFile.exists()) {
            plugin.saveResource("custom_mob_heads.yml", false);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        ConfigurationSection customMobsSection = config.getConfigurationSection("custom_mobs");
        if (customMobsSection == null) {
            plugin.getLogger().warning("No custom_mobs section found in custom_mob_heads.yml");
            return;
        }

        for (String internalName : customMobsSection.getKeys(false)) {
            String headId = customMobsSection.getString(internalName, "").trim();
            if (!headId.isBlank()) {
                customMobHeads.put(internalName.toLowerCase(Locale.ROOT), headId);
            }
        }
    }

    private String capitalizeWords(String str) {
        String[] words = str.toLowerCase().split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)))
                      .append(word.substring(1))
                      .append(" ");
            }
        }
        return result.toString().trim();
    }

    // ========== MOB HEAD DATABASE IDS ==========
    public static final Map<EntityType, String> MOB_HEAD_IDS = new HashMap<>();
    private static final Map<String, String> MYTHIC_HEAD_DISPLAY_NAMES = new HashMap<>();

    static {
        MYTHIC_HEAD_DISPLAY_NAMES.put("127453", "Bison Head");
        MYTHIC_HEAD_DISPLAY_NAMES.put("109562", "Blue Tit Head");
        MYTHIC_HEAD_DISPLAY_NAMES.put("68945", "Cardinal Head");
        MYTHIC_HEAD_DISPLAY_NAMES.put("86467", "Chameleon Head");
        MYTHIC_HEAD_DISPLAY_NAMES.put("124341", "Elephant Head");
        MYTHIC_HEAD_DISPLAY_NAMES.put("110727", "Giraffe Head");
        MYTHIC_HEAD_DISPLAY_NAMES.put("123659", "Grizzly Bear Head");
        MYTHIC_HEAD_DISPLAY_NAMES.put("49350", "Hedgehog Head");
        MYTHIC_HEAD_DISPLAY_NAMES.put("67856", "Hippo Head");
        MYTHIC_HEAD_DISPLAY_NAMES.put("57286", "Lion Head");
        MYTHIC_HEAD_DISPLAY_NAMES.put("120379", "Orca Head");
        MYTHIC_HEAD_DISPLAY_NAMES.put("98816", "Raccoon Head");
        MYTHIC_HEAD_DISPLAY_NAMES.put("107763", "Rat Head");
        MYTHIC_HEAD_DISPLAY_NAMES.put("81327", "Reindeer Head");
        MYTHIC_HEAD_DISPLAY_NAMES.put("3479", "Robin Head");
        MYTHIC_HEAD_DISPLAY_NAMES.put("4266", "Shark Head");
        MYTHIC_HEAD_DISPLAY_NAMES.put("50110", "Snake Head");
        MYTHIC_HEAD_DISPLAY_NAMES.put("57145", "Zebra Head");

        // Vanilla heads
        MOB_HEAD_IDS.put(EntityType.ZOMBIE, "ZOMBIE_HEAD");
        MOB_HEAD_IDS.put(EntityType.CREEPER, "CREEPER_HEAD");
        MOB_HEAD_IDS.put(EntityType.PIGLIN, "PIGLIN_HEAD");
        MOB_HEAD_IDS.put(EntityType.SKELETON, "SKELETON_SKULL");
        MOB_HEAD_IDS.put(EntityType.WITHER_SKELETON, "WITHER_SKELETON_SKULL");

        // Hostile Mobs (HeadDatabase IDs)
        MOB_HEAD_IDS.put(EntityType.SPIDER, "32706");
        MOB_HEAD_IDS.put(EntityType.ENDERMAN, "83741");
        MOB_HEAD_IDS.put(EntityType.WITCH, "71725");
        MOB_HEAD_IDS.put(EntityType.BLAZE, "47778");
        MOB_HEAD_IDS.put(EntityType.SLIME, "107343");
        MOB_HEAD_IDS.put(EntityType.GHAST, "26728");
        MOB_HEAD_IDS.put(EntityType.MAGMA_CUBE, "27398");
        MOB_HEAD_IDS.put(EntityType.PHANTOM, "57879");
        MOB_HEAD_IDS.put(EntityType.DROWNED, "15967");
        MOB_HEAD_IDS.put(EntityType.EVOKER, "71730");
        MOB_HEAD_IDS.put(EntityType.VINDICATOR, "28323");
        MOB_HEAD_IDS.put(EntityType.RAVAGER, "28196");
        MOB_HEAD_IDS.put(EntityType.HOGLIN, "89340");
        MOB_HEAD_IDS.put(EntityType.STRAY, "22401");
        MOB_HEAD_IDS.put(EntityType.PILLAGER, "25149");
        MOB_HEAD_IDS.put(EntityType.BREEZE, "69108");
        MOB_HEAD_IDS.put(EntityType.ELDER_GUARDIAN, "91880");
        MOB_HEAD_IDS.put(EntityType.GUARDIAN, "25292");
        MOB_HEAD_IDS.put(EntityType.ENDERMITE, "7375");
        MOB_HEAD_IDS.put(EntityType.HUSK, "41306");
        MOB_HEAD_IDS.put(EntityType.SILVERFISH, "106998");
        MOB_HEAD_IDS.put(EntityType.VEX, "3242");
        MOB_HEAD_IDS.put(EntityType.WARDEN, "81758");
        MOB_HEAD_IDS.put(EntityType.ZOMBIE_VILLAGER, "32951");
        MOB_HEAD_IDS.put(EntityType.ZOMBIFIED_PIGLIN, "37253");
        MOB_HEAD_IDS.put(EntityType.BOGGED, "87691");
        MOB_HEAD_IDS.put(EntityType.SHULKER, "71428");
        MOB_HEAD_IDS.put(EntityType.PIGLIN_BRUTE, "38372");

        // Passive Mobs
        MOB_HEAD_IDS.put(EntityType.COW, "71328");
        MOB_HEAD_IDS.put(EntityType.SHEEP, "57345");
        MOB_HEAD_IDS.put(EntityType.PIG, "91926");
        MOB_HEAD_IDS.put(EntityType.CHICKEN, "91925");
        MOB_HEAD_IDS.put(EntityType.HORSE, "49651");
        MOB_HEAD_IDS.put(EntityType.DONKEY, "18144");
        MOB_HEAD_IDS.put(EntityType.MULE, "38016");
        MOB_HEAD_IDS.put(EntityType.MOOSHROOM, "79760");
        MOB_HEAD_IDS.put(EntityType.OCELOT, "634");
        MOB_HEAD_IDS.put(EntityType.CAT, "103253");
        MOB_HEAD_IDS.put(EntityType.WOLF, "86442");
        MOB_HEAD_IDS.put(EntityType.FOX, "96812");
        MOB_HEAD_IDS.put(EntityType.POLAR_BEAR, "106990");
        MOB_HEAD_IDS.put(EntityType.LLAMA, "6822");
        MOB_HEAD_IDS.put(EntityType.PARROT, "49660");
        MOB_HEAD_IDS.put(EntityType.TURTLE, "78602");
        MOB_HEAD_IDS.put(EntityType.PANDA, "106986");
        MOB_HEAD_IDS.put(EntityType.RABBIT, "99933");
        MOB_HEAD_IDS.put(EntityType.GOAT, "43601");
        MOB_HEAD_IDS.put(EntityType.STRIDER, "71453");
        MOB_HEAD_IDS.put(EntityType.DOLPHIN, "16799");
        MOB_HEAD_IDS.put(EntityType.SQUID, "40854");
        MOB_HEAD_IDS.put(EntityType.GLOW_SQUID, "56652");
        MOB_HEAD_IDS.put(EntityType.AXOLOTL, "89353");
        MOB_HEAD_IDS.put(EntityType.CAMEL, "58125");
        MOB_HEAD_IDS.put(EntityType.VILLAGER, "90692");
        MOB_HEAD_IDS.put(EntityType.WANDERING_TRADER, "65745");
        MOB_HEAD_IDS.put(EntityType.ALLAY, "111710");
        MOB_HEAD_IDS.put(EntityType.ARMADILLO, "74256");
        MOB_HEAD_IDS.put(EntityType.BAT, "47787");
        MOB_HEAD_IDS.put(EntityType.COD, "17898");
        MOB_HEAD_IDS.put(EntityType.FROG, "69080");
        MOB_HEAD_IDS.put(EntityType.PUFFERFISH, "45707");
        MOB_HEAD_IDS.put(EntityType.SALMON, "78542");
        MOB_HEAD_IDS.put(EntityType.SNOW_GOLEM, "109991");
        MOB_HEAD_IDS.put(EntityType.TADPOLE, "51348");
        MOB_HEAD_IDS.put(EntityType.TROPICAL_FISH, "30881");
        MOB_HEAD_IDS.put(EntityType.SNIFFER, "60697");
        MOB_HEAD_IDS.put(EntityType.IRON_GOLEM, "81656");
        MOB_HEAD_IDS.put(EntityType.BEE, "108538");
        MOB_HEAD_IDS.put(EntityType.CAVE_SPIDER, "315");
        MOB_HEAD_IDS.put(EntityType.TRADER_LLAMA, "49645");
        MOB_HEAD_IDS.put(EntityType.SKELETON_HORSE, "105331");
    }

    // ========== SPAWN EGG MATERIALS ==========
    public static final Map<EntityType, Material> SPAWN_EGG_MATERIALS = new HashMap<>();

    static {
        SPAWN_EGG_MATERIALS.put(EntityType.ZOMBIE, Material.ZOMBIE_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.SKELETON, Material.SKELETON_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.CREEPER, Material.CREEPER_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.SPIDER, Material.SPIDER_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.ENDERMAN, Material.ENDERMAN_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.WITCH, Material.WITCH_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.BLAZE, Material.BLAZE_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.PIGLIN, Material.PIGLIN_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.WITHER_SKELETON, Material.WITHER_SKELETON_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.SLIME, Material.SLIME_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.GHAST, Material.GHAST_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.MAGMA_CUBE, Material.MAGMA_CUBE_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.PHANTOM, Material.PHANTOM_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.DROWNED, Material.DROWNED_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.EVOKER, Material.EVOKER_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.VINDICATOR, Material.VINDICATOR_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.RAVAGER, Material.RAVAGER_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.HOGLIN, Material.HOGLIN_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.STRAY, Material.STRAY_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.PILLAGER, Material.PILLAGER_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.BREEZE, Material.BREEZE_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.ELDER_GUARDIAN, Material.ELDER_GUARDIAN_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.GUARDIAN, Material.GUARDIAN_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.ENDERMITE, Material.ENDERMITE_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.HUSK, Material.HUSK_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.SILVERFISH, Material.SILVERFISH_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.VEX, Material.VEX_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.WARDEN, Material.WARDEN_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.ZOMBIE_VILLAGER, Material.ZOMBIE_VILLAGER_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.ZOMBIFIED_PIGLIN, Material.ZOMBIFIED_PIGLIN_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.BOGGED, Material.BOGGED_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.PIGLIN_BRUTE, Material.PIGLIN_BRUTE_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.SHULKER, Material.SHULKER_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.COW, Material.COW_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.SHEEP, Material.SHEEP_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.PIG, Material.PIG_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.CHICKEN, Material.CHICKEN_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.HORSE, Material.HORSE_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.DONKEY, Material.DONKEY_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.MULE, Material.MULE_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.MOOSHROOM, Material.MOOSHROOM_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.OCELOT, Material.OCELOT_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.CAT, Material.CAT_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.WOLF, Material.WOLF_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.FOX, Material.FOX_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.POLAR_BEAR, Material.POLAR_BEAR_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.LLAMA, Material.LLAMA_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.PARROT, Material.PARROT_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.TURTLE, Material.TURTLE_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.PANDA, Material.PANDA_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.RABBIT, Material.RABBIT_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.GOAT, Material.GOAT_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.STRIDER, Material.STRIDER_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.DOLPHIN, Material.DOLPHIN_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.SQUID, Material.SQUID_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.GLOW_SQUID, Material.GLOW_SQUID_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.AXOLOTL, Material.AXOLOTL_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.CAMEL, Material.CAMEL_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.VILLAGER, Material.VILLAGER_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.WANDERING_TRADER, Material.WANDERING_TRADER_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.ALLAY, Material.ALLAY_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.ARMADILLO, Material.ARMADILLO_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.BAT, Material.BAT_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.COD, Material.COD_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.FROG, Material.FROG_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.PUFFERFISH, Material.PUFFERFISH_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.SALMON, Material.SALMON_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.SNOW_GOLEM, Material.SNOW_GOLEM_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.TADPOLE, Material.TADPOLE_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.TROPICAL_FISH, Material.TROPICAL_FISH_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.IRON_GOLEM, Material.IRON_GOLEM_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.BEE, Material.BEE_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.CAVE_SPIDER, Material.CAVE_SPIDER_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.TRADER_LLAMA, Material.TRADER_LLAMA_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.SKELETON_HORSE, Material.SKELETON_HORSE_SPAWN_EGG);
        SPAWN_EGG_MATERIALS.put(EntityType.SNIFFER, Material.SNIFFER_SPAWN_EGG);
    }
}
