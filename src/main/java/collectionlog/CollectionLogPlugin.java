package collectionlog;

import collectionlog.anticheat.ContainerTrackingManager;
import collectionlog.anticheat.ItemTrackingManager;
import collectionlog.collections.CollectionLoader;
import collectionlog.collections.SpecialCollectionLoader;
import collectionlog.database.AsyncTaskManager;
import collectionlog.database.DatabaseManager;
import collectionlog.database.DatabaseType;
import collectionlog.database.dao.BlockOwnershipDAO;
import collectionlog.database.dao.ContainerItemDAO;
import collectionlog.database.dao.PlayerCollectionDAO;
import collectionlog.listeners.BlockBreakListener;
import collectionlog.listeners.BlockPlaceListener;
import collectionlog.listeners.ContainerListener;
import collectionlog.listeners.ExplosionListener;
import collectionlog.listeners.ItemPickupListener;
import collectionlog.listeners.PistonListener;
import collectionlog.listeners.PlayerDropItemListener;
import collectionlog.managers.ProgressTrackingManager;
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

            // Initialize managers
            itemTrackingManager = new ItemTrackingManager(this);
            containerTrackingManager = new ContainerTrackingManager(this, containerItemDAO, asyncTaskManager);
            getLogger().info("✅ Managers initialized");

            // Register listeners
            getServer().getPluginManager().registerEvents(new BlockPlaceListener(this), this);
            getServer().getPluginManager().registerEvents(new BlockBreakListener(this), this);
            getServer().getPluginManager().registerEvents(new PistonListener(this), this);
            getServer().getPluginManager().registerEvents(new ExplosionListener(this), this);
            getServer().getPluginManager().registerEvents(new PlayerDropItemListener(this), this);
            getServer().getPluginManager().registerEvents(new ItemPickupListener(this), this);
            getServer().getPluginManager().registerEvents(new ContainerListener(this), this);
            getLogger().info("✅ Listeners registered");

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
}
