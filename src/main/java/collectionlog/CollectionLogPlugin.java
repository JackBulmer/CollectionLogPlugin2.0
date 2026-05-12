package collectionlog;

import collectionlog.anticheat.ContainerTrackingManager;
import collectionlog.anticheat.ItemTrackingManager;
import collectionlog.collections.CollectionLoader;
import collectionlog.collections.SpecialCollectionLoader;
import collectionlog.commands.AdminCommands;
import collectionlog.commands.CraftCommand;
import collectionlog.commands.DyeCommand;
import collectionlog.commands.GlowingCommand;
import collectionlog.commands.HarvestCommand;
import collectionlog.commands.InstantGlassCommand;
import collectionlog.commands.LogScoreboardCommand;
import collectionlog.commands.LogStatsCommand;
import collectionlog.commands.LogsCommand;
import collectionlog.commands.PerksCommand;
import collectionlog.commands.TagsCommand;
import collectionlog.commands.TunnelMinerCommand;
import collectionlog.commands.VeinMinerCommand;
import collectionlog.leaderboard.HologramManager;
import collectionlog.leaderboard.LeaderboardManager;
import collectionlog.teleport.HomeManager;
import collectionlog.teleport.TeleportManager;
import collectionlog.teleport.TeleportRequestManager;
import collectionlog.teleport.TeleportSettingsManager;
import collectionlog.teleport.TeleportValidator;
import collectionlog.teleport.commands.DelHomeCommand;
import collectionlog.teleport.commands.HomeCommand;
import collectionlog.teleport.commands.HomesCommand;
import collectionlog.teleport.commands.SetHomeCommand;
import collectionlog.teleport.commands.TeleportAcceptCommand;
import collectionlog.teleport.commands.TeleportAdminCommand;
import collectionlog.teleport.commands.TeleportOverrideCommand;
import collectionlog.teleport.commands.TeleportRequestCommand;
import collectionlog.teleport.gui.HomesGUI;
import collectionlog.database.AsyncTaskManager;
import collectionlog.database.DatabaseManager;
import collectionlog.database.DatabaseType;
import collectionlog.database.dao.BlockOwnershipDAO;
import collectionlog.database.dao.ContainerItemDAO;
import collectionlog.database.dao.PlayerCollectionDAO;
import collectionlog.gui.CollectionGUI;
import collectionlog.gui.GlowingGUI;
import collectionlog.gui.InfiniteDyesGUI;
import collectionlog.gui.PerksGUI;
import collectionlog.gui.TagsGUI;
import collectionlog.perks.InfiniteLavaBucket;
import collectionlog.perks.PerkManager;
import collectionlog.perks.PhantomTogglePerk;
import collectionlog.perks.PotionEffectManager;
import collectionlog.perks.ExtraHeartsManager;
import collectionlog.perks.enderchest.LargerEnderChestListener;
import collectionlog.perks.enderchest.LargerEnderChestManager;
import collectionlog.listeners.DoublSnifferFlowersListener;
import collectionlog.listeners.HarvestAndReplantListener;
import collectionlog.listeners.OpenableShulkersListener;
import collectionlog.listeners.InfiniteLavaBucketListener;
import collectionlog.listeners.InstantGlassSmeltingListener;
import collectionlog.listeners.StructureGrowListener;
import collectionlog.listeners.TreeCapitatorListener;
import collectionlog.listeners.TunnelMinerListener;
import collectionlog.listeners.VeinMinerListener;
import collectionlog.listeners.BlockBreakListener;
import collectionlog.listeners.BlockPlaceListener;
import collectionlog.listeners.BrewingStandListener;
import collectionlog.listeners.BucketListener;
import collectionlog.listeners.ContainerListener;
import collectionlog.listeners.NaturalContainerTrackingListener;
import collectionlog.crafting.CustomCraftingListener;
import collectionlog.crafting.CustomRecipeManager;
import collectionlog.listeners.CraftItemListener;
import collectionlog.listeners.EnchantmentTableListener;
import collectionlog.listeners.EnchantmentTrackingListener;
import collectionlog.listeners.EntityDeathListener;
import collectionlog.listeners.ExplosionListener;
import collectionlog.listeners.FastGhastRidingListener;
import collectionlog.listeners.InventoryClickListener;
import collectionlog.listeners.ItemPickupListener;
import collectionlog.listeners.MobHeadDropListener;
import collectionlog.listeners.PistonListener;
import collectionlog.listeners.PlayerFishListener;
import collectionlog.listeners.BedrockBreakListener;
import collectionlog.listeners.PlayerDropItemListener;
import collectionlog.listeners.PlayerJoinListener;
import collectionlog.listeners.QuickerPotionBrewingListener;
import collectionlog.listeners.SilkTouchObtainableListener;
import collectionlog.listeners.SmithingTableListener;
import collectionlog.listeners.StonecutterListener;
import collectionlog.listeners.StrafeListener;
import collectionlog.listeners.VillagerTradingListener;
import collectionlog.managers.ProgressTrackingManager;
import collectionlog.managers.GlowingManager;
import collectionlog.managers.InfiniteDyesManager;
import collectionlog.managers.StrafeManager;
import collectionlog.milestones.MilestoneManager;
import collectionlog.stats.CollectionStatsFileManager;
import collectionlog.tags.TagManager;
import collectionlog.utils.CollectionMatcher;
import collectionlog.utils.GriefPreventionUtil;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.logging.Level;

public class CollectionLogPlugin extends JavaPlugin {
    private static CollectionLogPlugin instance;

    private DatabaseManager databaseManager;
    private AsyncTaskManager asyncTaskManager;
    private BlockOwnershipDAO blockOwnershipDAO;
    private ContainerItemDAO containerItemDAO;
    private ItemTrackingManager itemTrackingManager;
    private ContainerTrackingManager containerTrackingManager;
    private CollectionLoader collectionLoader;
    private SpecialCollectionLoader specialCollectionLoader;
    private PlayerCollectionDAO playerCollectionDAO;
    private ProgressTrackingManager progressTrackingManager;
    private MilestoneManager milestoneManager;
    private CollectionMatcher collectionMatcher;
    private CollectionGUI collectionGUI;
    private StrafeManager strafeManager;
    private TagManager tagManager;
    private TagsGUI tagsGUI;
    private PerkManager perkManager;
    private PotionEffectManager potionEffectManager;
    private ExtraHeartsManager extraHeartsManager;
    private PerksGUI perksGUI;
    private CustomRecipeManager customRecipeManager;
    private InfiniteLavaBucket infiniteLavaBucket;
    private GlowingManager glowingManager;
    private GlowingGUI glowingGUI;
    private InfiniteDyesManager infiniteDyesManager;
    private InfiniteDyesGUI infiniteDyesGUI;
    private InstantGlassSmeltingListener instantGlassSmeltingListener;
    private QuickerPotionBrewingListener quickerPotionBrewingListener;
    private VeinMinerListener veinMinerListener;
    private TunnelMinerListener tunnelMinerListener;
    private HarvestAndReplantListener harvestAndReplantListener;
    private TreeCapitatorListener treeCapitatorListener;
    private StructureGrowListener structureGrowListener;
    private HomeManager homeManager;
    private TeleportSettingsManager teleportSettingsManager;
    private TeleportRequestManager teleportRequestManager;
    private TeleportValidator teleportValidator;
    private TeleportManager teleportManager;
    private HomesGUI homesGUI;
    private TeleportAdminCommand teleportAdminCommand;
    private LargerEnderChestManager largerEnderChestManager;
    private LargerEnderChestListener largerEnderChestListener;
    private LeaderboardManager leaderboardManager;
    private CollectionStatsFileManager collectionStatsFileManager;
    private HologramManager hologramManager;

    @Override
    public void onEnable() {
        instance = this;

        getLogger().info("=================================");
        getLogger().info("  CollectionLogPlugin V2.0");
        getLogger().info("=================================");

        // Save default config
        saveDefaultConfig();

        // Initialize async task manager
        asyncTaskManager = new AsyncTaskManager(this);
        getLogger().info("✅ Async task manager initialized");

        // Initialize database
        try {
            databaseManager = new DatabaseManager(this);

            String dbType = getConfig().getString("database.type", "sqlite").toUpperCase();
            DatabaseType type = DatabaseType.valueOf(dbType);

            if (type == DatabaseType.SQLITE) {
                databaseManager.initialize(type, null, 0, null, null, null);
            } else {
                String host = getConfig().getString("database.host", "localhost");
                int port = getConfig().getInt("database.port", 3306);
                String database = getConfig().getString("database.database", "collectionlog");
                String username = getConfig().getString("database.username", "root");
                String password = getConfig().getString("database.password", "");

                databaseManager.initialize(type, host, port, database, username, password);
            }

            getLogger().info("✅ Database connected successfully");
            // Initialize DAOs
            blockOwnershipDAO = new BlockOwnershipDAO(this, databaseManager);
            containerItemDAO = new ContainerItemDAO(this, databaseManager);
            getLogger().info("✅ DAOs initialized");

            // Initialize collection loaders
            try {
                collectionLoader = new CollectionLoader(this);
                collectionLoader.loadCollections();

                specialCollectionLoader = new SpecialCollectionLoader(this);
                specialCollectionLoader.loadSpecialCollections();
            } catch (RuntimeException e) {
                // Loaders throw runtime exceptions for malformed JSON and file IO issues.
                getLogger().log(Level.SEVERE, "❌ Failed to load collection definitions", e);
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
            getLogger().info("✅ Collection loaders initialized");

            // Initialize PlayerCollectionDAO
            playerCollectionDAO = new PlayerCollectionDAO(this, databaseManager, asyncTaskManager);

            // Initialize ProgressTrackingManager
            progressTrackingManager = new ProgressTrackingManager(this, playerCollectionDAO, asyncTaskManager);
            getLogger().info("✅ Progress tracking initialized");

            // Initialize milestone manager
            milestoneManager = new MilestoneManager(this);
            getLogger().info("✅ Milestone system initialized");

            collectionGUI = new CollectionGUI(this);

            // Initialize collection matcher
            collectionMatcher = new CollectionMatcher(collectionLoader, specialCollectionLoader);
            getLogger().info("✅ Collection matcher initialized");

            // Initialize managers
            itemTrackingManager = new ItemTrackingManager(this);
            containerTrackingManager = new ContainerTrackingManager(this, containerItemDAO, asyncTaskManager);
            strafeManager = new StrafeManager(this);
            tagManager = new TagManager(this);
            tagsGUI = new TagsGUI(this, tagManager);
            perkManager = new PerkManager(this);
            potionEffectManager = new PotionEffectManager(this);
            extraHeartsManager = new ExtraHeartsManager(this);
            perksGUI = new PerksGUI(this, perkManager, tagManager);
            customRecipeManager = new CustomRecipeManager(this);
            customRecipeManager.loadRecipes();
            infiniteLavaBucket = new InfiniteLavaBucket(this);
            glowingManager = new GlowingManager(this);
            glowingGUI = new GlowingGUI(this);
            infiniteDyesManager = new InfiniteDyesManager();
            infiniteDyesGUI = new InfiniteDyesGUI(this);
            largerEnderChestManager = new LargerEnderChestManager(this);
            largerEnderChestManager.loadCondensingPlayersAsync();
            largerEnderChestListener = new LargerEnderChestListener(this, largerEnderChestManager);
            leaderboardManager = new LeaderboardManager(this);
            collectionStatsFileManager = new CollectionStatsFileManager(this, leaderboardManager);
            hologramManager = new HologramManager(this, leaderboardManager);
            leaderboardManager.refreshAllPlayerStats().whenComplete((ignored, throwable) -> {
                if (throwable != null) {
                    getLogger().log(Level.SEVERE, "Failed to refresh leaderboard stats on startup", throwable);
                }
                collectionStatsFileManager.generateStatsFile();
                hologramManager.updateHolograms();
            });
            hologramManager.loadHologramsFromDatabase();
            hologramManager.start();
            getLogger().info("✅ Managers initialized");

            // Initialize GriefPrevention integration
            GriefPreventionUtil.initialize(this);

            // Register listeners
            getServer().getPluginManager().registerEvents(new BlockPlaceListener(this), this);
            getServer().getPluginManager().registerEvents(new BlockBreakListener(this), this);
            getServer().getPluginManager().registerEvents(new PistonListener(this), this);
            getServer().getPluginManager().registerEvents(new ExplosionListener(this), this);
            getServer().getPluginManager().registerEvents(new PlayerDropItemListener(this), this);
            getServer().getPluginManager().registerEvents(new ItemPickupListener(this), this);
            getServer().getPluginManager().registerEvents(new ContainerListener(this), this);
            getServer().getPluginManager().registerEvents(new NaturalContainerTrackingListener(this), this);
            getServer().getPluginManager().registerEvents(new EntityDeathListener(this), this);
            getServer().getPluginManager().registerEvents(new MobHeadDropListener(this), this);
            getServer().getPluginManager().registerEvents(new PlayerFishListener(this), this);
            getServer().getPluginManager().registerEvents(new CraftItemListener(this), this);
            getServer().getPluginManager().registerEvents(new InventoryClickListener(this), this);
            getServer().getPluginManager().registerEvents(new BrewingStandListener(this), this);
            getServer().getPluginManager().registerEvents(new BucketListener(this), this);
            getServer().getPluginManager().registerEvents(new EnchantmentTableListener(this), this);
            getServer().getPluginManager().registerEvents(new EnchantmentTrackingListener(this), this);
            getServer().getPluginManager().registerEvents(new SmithingTableListener(this), this);
            getServer().getPluginManager().registerEvents(new StonecutterListener(this), this);
            getServer().getPluginManager().registerEvents(new VillagerTradingListener(this), this);
            getServer().getPluginManager().registerEvents(new BedrockBreakListener(this), this);
            getServer().getPluginManager().registerEvents(new SilkTouchObtainableListener(this), this);
            getServer().getPluginManager().registerEvents(new StrafeListener(this, strafeManager), this);
            getServer().getPluginManager().registerEvents(new PhantomTogglePerk(this), this);
            getServer().getPluginManager().registerEvents(new InfiniteLavaBucketListener(this), this);
            instantGlassSmeltingListener = new InstantGlassSmeltingListener(this);
            getServer().getPluginManager().registerEvents(instantGlassSmeltingListener, this);
            quickerPotionBrewingListener = new QuickerPotionBrewingListener(this);
            getServer().getPluginManager().registerEvents(quickerPotionBrewingListener, this);
            veinMinerListener = new VeinMinerListener(this);
            getServer().getPluginManager().registerEvents(veinMinerListener, this);
            tunnelMinerListener = new TunnelMinerListener(this);
            getServer().getPluginManager().registerEvents(tunnelMinerListener, this);
            harvestAndReplantListener = new HarvestAndReplantListener(this);
            getServer().getPluginManager().registerEvents(harvestAndReplantListener, this);
            getServer().getPluginManager().registerEvents(new DoublSnifferFlowersListener(this), this);
            getServer().getPluginManager().registerEvents(new OpenableShulkersListener(this), this);
            getServer().getPluginManager().registerEvents(new FastGhastRidingListener(this), this);
            getServer().getPluginManager().registerEvents(largerEnderChestListener, this);

            // Schedule cleanup task for protected crops (runs every second)
            getServer().getScheduler().runTaskTimer(this, () ->
                    harvestAndReplantListener.cleanupExpiredProtections(), 20L, 20L);
            getServer().getPluginManager().registerEvents(potionEffectManager, this);
            getServer().getPluginManager().registerEvents(collectionGUI, this);
            getServer().getPluginManager().registerEvents(tagsGUI, this);
            getServer().getPluginManager().registerEvents(perksGUI, this);
            getServer().getPluginManager().registerEvents(glowingGUI, this);
            getServer().getPluginManager().registerEvents(infiniteDyesGUI, this);
            getServer().getPluginManager().registerEvents(glowingManager, this);
            getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
            getServer().getPluginManager().registerEvents(new CustomCraftingListener(this), this);
            treeCapitatorListener = new TreeCapitatorListener(this);
            getServer().getPluginManager().registerEvents(treeCapitatorListener, this);
            structureGrowListener = new StructureGrowListener(this);
            getServer().getPluginManager().registerEvents(structureGrowListener, this);

            // Initialize teleport system
            homeManager = new HomeManager(this);
            teleportSettingsManager = new TeleportSettingsManager(this);
            teleportRequestManager = new TeleportRequestManager(this);
            teleportValidator = new TeleportValidator(this);
            teleportManager = new TeleportManager(this);
            homesGUI = new HomesGUI(this, homeManager, teleportManager, teleportSettingsManager, teleportValidator);
            teleportAdminCommand = new TeleportAdminCommand(this, teleportSettingsManager);
            getServer().getPluginManager().registerEvents(teleportManager, this);
            getServer().getPluginManager().registerEvents(homesGUI, this);

            // Schedule auto-disable check for Tree Capitator (every 60 seconds)
            getServer().getScheduler().runTaskTimer(this, () -> {
                if (treeCapitatorListener != null) {
                    treeCapitatorListener.checkInactivity();
                }
            }, 1200L, 1200L);
            getLogger().info("✅ Listeners registered");

            PluginCommand logsCommand = getCommand("logs");
            if (logsCommand != null) {
                LogsCommand executor = new LogsCommand(this);
                logsCommand.setExecutor(executor);
                logsCommand.setTabCompleter(executor);
            }

            PluginCommand adminCommand = getCommand("cl");
            if (adminCommand != null) {
                AdminCommands executor = new AdminCommands(this);
                adminCommand.setExecutor(executor);
                adminCommand.setTabCompleter(executor);
            }

            PluginCommand tagsCommand = getCommand("tags");
            if (tagsCommand != null) {
                tagsCommand.setExecutor(new TagsCommand(this));
            } else {
                getLogger().warning("Command 'tags' not found in plugin.yml!");
            }

            PluginCommand perksCommand = getCommand("perks");
            if (perksCommand != null) {
                perksCommand.setExecutor(new PerksCommand(this));
            } else {
                getLogger().warning("Command 'perks' not found in plugin.yml!");
            }

            PluginCommand craftCommand = getCommand("craft");
            if (craftCommand != null) {
                craftCommand.setExecutor(new CraftCommand(this));
            } else {
                getLogger().warning("Command 'craft' not found in plugin.yml!");
            }

            PluginCommand glowingCommand = getCommand("glowing");
            if (glowingCommand != null) {
                GlowingCommand glowingExecutor = new GlowingCommand(this);
                glowingCommand.setExecutor(glowingExecutor);
                glowingCommand.setTabCompleter(glowingExecutor);
            } else {
                getLogger().warning("Command 'glowing' not found in plugin.yml!");
            }

            PluginCommand dyeCommand = getCommand("dye");
            if (dyeCommand != null) {
                dyeCommand.setExecutor(new DyeCommand(this));
            } else {
                getLogger().warning("Command 'dye' not found in plugin.yml!");
            }

            PluginCommand instantGlassCommand = getCommand("instantglass");
            if (instantGlassCommand != null) {
                instantGlassCommand.setExecutor(new InstantGlassCommand(this));
            } else {
                getLogger().warning("Command 'instantglass' not found in plugin.yml!");
            }

            PluginCommand veinMinerCommand = getCommand("veinminer");
            if (veinMinerCommand != null) {
                veinMinerCommand.setExecutor(new VeinMinerCommand(this));
            } else {
                getLogger().warning("Command 'veinminer' not found in plugin.yml!");
            }

            PluginCommand tunnelMinerCommand = getCommand("tunnelminer");
            if (tunnelMinerCommand != null) {
                tunnelMinerCommand.setExecutor(new TunnelMinerCommand(this));
            } else {
                getLogger().warning("Command 'tunnelminer' not found in plugin.yml!");
            }

            PluginCommand harvestCommand = getCommand("harvest");
            if (harvestCommand != null) {
                harvestCommand.setExecutor(new HarvestCommand(this));
            } else {
                getLogger().warning("Command 'harvest' not found in plugin.yml!");
            }

            PluginCommand logStatsCommand = getCommand("logstats");
            if (logStatsCommand != null) {
                logStatsCommand.setExecutor(new LogStatsCommand(this));
            } else {
                getLogger().warning("Command 'logstats' not found in plugin.yml!");
            }

            PluginCommand logScoreboardCommand = getCommand("logscoreboard");
            if (logScoreboardCommand != null) {
                LogScoreboardCommand executor = new LogScoreboardCommand(this);
                logScoreboardCommand.setExecutor(executor);
                logScoreboardCommand.setTabCompleter(executor);
            } else {
                getLogger().warning("Command 'logscoreboard' not found in plugin.yml!");
            }

            PluginCommand setHomeCommand = getCommand("sethome");
            if (setHomeCommand != null) {
                SetHomeCommand setHomeExec = new SetHomeCommand(this, homeManager, teleportSettingsManager, teleportValidator);
                setHomeCommand.setExecutor(setHomeExec);
                setHomeCommand.setTabCompleter(setHomeExec);
            }

            PluginCommand delHomeCommand = getCommand("delhome");
            if (delHomeCommand != null) {
                DelHomeCommand delHomeExec = new DelHomeCommand(this, homeManager, teleportValidator);
                delHomeCommand.setExecutor(delHomeExec);
                delHomeCommand.setTabCompleter(delHomeExec);
            }

            PluginCommand homeCommand = getCommand("home");
            if (homeCommand != null) {
                HomeCommand homeExec = new HomeCommand(this, homeManager, teleportManager, teleportSettingsManager, teleportValidator);
                homeCommand.setExecutor(homeExec);
                homeCommand.setTabCompleter(homeExec);
            }

            PluginCommand homesCommand = getCommand("homes");
            if (homesCommand != null) {
                homesCommand.setExecutor(new HomesCommand(this, homesGUI, teleportValidator));
            }

            PluginCommand tprCommand = getCommand("tpr");
            if (tprCommand != null) {
                TeleportRequestCommand tprExec = new TeleportRequestCommand(this, teleportRequestManager, teleportValidator);
                tprCommand.setExecutor(tprExec);
                tprCommand.setTabCompleter(tprExec);
            }

            PluginCommand tpaCommand = getCommand("tpa");
            if (tpaCommand != null) {
                TeleportAcceptCommand tpaExec = new TeleportAcceptCommand(this, teleportRequestManager, teleportManager, teleportSettingsManager, teleportValidator);
                tpaCommand.setExecutor(tpaExec);
                tpaCommand.setTabCompleter(tpaExec);
            }

            PluginCommand tpCommand = getCommand("tp");
            if (tpCommand != null) {
                TeleportOverrideCommand tpExec = new TeleportOverrideCommand(this, homeManager, teleportManager, teleportSettingsManager, teleportValidator);
                tpCommand.setExecutor(tpExec);
                tpCommand.setTabCompleter(tpExec);
            }
            getLogger().info("✅ GUI and commands registered");

        } catch (SQLException e) {
            getLogger().severe("❌ Failed to initialize database: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getLogger().info("✅ CollectionLogPlugin V2.0 enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Shutting down CollectionLogPlugin V2.0...");

        // Clear item tracking
        if (itemTrackingManager != null) {
            itemTrackingManager.clearAll();
        }
        if (containerTrackingManager != null) {
            containerTrackingManager.clearCache();
        }
        if (quickerPotionBrewingListener != null) {
            quickerPotionBrewingListener.shutdown();
        }
        if (hologramManager != null) {
            hologramManager.shutdown();
        }

        // Shutdown async tasks
        if (asyncTaskManager != null) {
            asyncTaskManager.shutdown();
        }

        // Close database connections
        if (databaseManager != null) {
            databaseManager.shutdown();
        }

        getLogger().info("✅ CollectionLogPlugin V2.0 disabled");
    }

    public static CollectionLogPlugin getInstance() {
        return instance;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public AsyncTaskManager getAsyncTaskManager() {
        return asyncTaskManager;
    }

    public BlockOwnershipDAO getBlockOwnershipDAO() {
        return blockOwnershipDAO;
    }

    public ItemTrackingManager getItemTrackingManager() {
        return itemTrackingManager;
    }

    public ContainerItemDAO getContainerItemDAO() {
        return containerItemDAO;
    }

    public ContainerTrackingManager getContainerTrackingManager() {
        return containerTrackingManager;
    }

    public CollectionLoader getCollectionLoader() {
        return collectionLoader;
    }

    public SpecialCollectionLoader getSpecialCollectionLoader() {
        return specialCollectionLoader;
    }

    public PlayerCollectionDAO getPlayerCollectionDAO() {
        return playerCollectionDAO;
    }

    public ProgressTrackingManager getProgressTrackingManager() {
        return progressTrackingManager;
    }

    public CollectionMatcher getCollectionMatcher() {
        return collectionMatcher;
    }

    public MilestoneManager getMilestoneManager() {
        return milestoneManager;
    }

    public CollectionGUI getCollectionGUI() {
        return collectionGUI;
    }

    public StrafeManager getStrafeManager() {
        return strafeManager;
    }

    public TagManager getTagManager() {
        return tagManager;
    }

    public TagsGUI getTagsGUI() {
        return tagsGUI;
    }

    public PerkManager getPerkManager() {
        return perkManager;
    }

    public PotionEffectManager getPotionEffectManager() {
        return potionEffectManager;
    }

    public ExtraHeartsManager getExtraHeartsManager() {
        return extraHeartsManager;
    }

    public PerksGUI getPerksGUI() {
        return perksGUI;
    }

    public CustomRecipeManager getCustomRecipeManager() {
        return customRecipeManager;
    }

    public InfiniteLavaBucket getInfiniteLavaBucket() {
        return infiniteLavaBucket;
    }

    public GlowingManager getGlowingManager() {
        return glowingManager;
    }

    public GlowingGUI getGlowingGUI() {
        return glowingGUI;
    }

    public InfiniteDyesManager getInfiniteDyesManager() {
        return infiniteDyesManager;
    }

    public InfiniteDyesGUI getInfiniteDyesGUI() {
        return infiniteDyesGUI;
    }

    public InstantGlassSmeltingListener getInstantGlassSmeltingListener() {
        return instantGlassSmeltingListener;
    }

    public VeinMinerListener getVeinMinerListener() {
        return veinMinerListener;
    }

    public TunnelMinerListener getTunnelMinerListener() {
        return tunnelMinerListener;
    }

    public HarvestAndReplantListener getHarvestAndReplantListener() {
        return harvestAndReplantListener;
    }

    public TreeCapitatorListener getTreeCapitatorListener() {
        return treeCapitatorListener;
    }

    public LargerEnderChestManager getLargerEnderChestManager() {
        return largerEnderChestManager;
    }

    public StructureGrowListener getStructureGrowListener() {
        return structureGrowListener;
    }

    public HomeManager getHomeManager() {
        return homeManager;
    }

    public TeleportSettingsManager getTeleportSettingsManager() {
        return teleportSettingsManager;
    }

    public TeleportRequestManager getTeleportRequestManager() {
        return teleportRequestManager;
    }

    public TeleportValidator getTeleportValidator() {
        return teleportValidator;
    }

    public TeleportManager getTeleportManager() {
        return teleportManager;
    }

    public HomesGUI getHomesGUI() {
        return homesGUI;
    }

    public TeleportAdminCommand getTeleportAdminCommand() {
        return teleportAdminCommand;
    }

    public LeaderboardManager getLeaderboardManager() {
        return leaderboardManager;
    }

    public CollectionStatsFileManager getCollectionStatsFileManager() {
        return collectionStatsFileManager;
    }

    public HologramManager getHologramManager() {
        return hologramManager;
    }
}
