package collectionlog;

import collectionlog.anticheat.ItemTrackingManager;
import collectionlog.database.AsyncTaskManager;
import collectionlog.database.DatabaseManager;
import collectionlog.database.DatabaseType;
import collectionlog.database.dao.BlockOwnershipDAO;
import collectionlog.listeners.BlockBreakListener;
import collectionlog.listeners.BlockPlaceListener;
import collectionlog.listeners.ExplosionListener;
import collectionlog.listeners.PistonListener;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;

public class CollectionLogPlugin extends JavaPlugin {
    private static CollectionLogPlugin instance;

    private DatabaseManager databaseManager;
    private AsyncTaskManager asyncTaskManager;
    private BlockOwnershipDAO blockOwnershipDAO;
    private ItemTrackingManager itemTrackingManager;

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
            blockOwnershipDAO = new BlockOwnershipDAO(this, databaseManager);
            getLogger().info("✅ DAOs initialized");
            itemTrackingManager = new ItemTrackingManager(this);
            getLogger().info("✅ Managers initialized");

            getServer().getPluginManager().registerEvents(new BlockPlaceListener(this), this);
            getServer().getPluginManager().registerEvents(new BlockBreakListener(this), this);
            getServer().getPluginManager().registerEvents(new PistonListener(this), this);
            getServer().getPluginManager().registerEvents(new ExplosionListener(this), this);
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
}
