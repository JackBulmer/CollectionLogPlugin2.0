package collectionlog.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.Plugin;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class DatabaseManager {
    private static final int LOG_SQL_PREVIEW_LENGTH = 50;
    private final Plugin plugin;
    private HikariDataSource dataSource;
    private DatabaseType databaseType;

    public DatabaseManager(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Initialize database connection pool
     */
    public void initialize(DatabaseType type, String host, int port,
                           String database, String username, String password) throws SQLException {
        this.databaseType = type;

        HikariConfig config = new HikariConfig();

        if (type == DatabaseType.SQLITE) {
            config.setJdbcUrl("jdbc:sqlite:" + plugin.getDataFolder().getAbsolutePath() + "/data.db");
            config.setDriverClassName("org.sqlite.JDBC");
        } else if (type == DatabaseType.MYSQL) {
            config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                    + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            config.setUsername(username);
            config.setPassword(password);
        }

        // Connection pool settings
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setConnectionTestQuery("SELECT 1");
        config.setPoolName("CollectionLogPool");

        dataSource = new HikariDataSource(config);

        // Initialize schema
        initializeSchema();

        plugin.getLogger().info("✅ Database initialized (" + type + ")");
    }

    /**
     * Get a connection from the pool
     */
    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Database connection pool is not initialized");
        }
        return dataSource.getConnection();
    }

    /**
     * Initialize database schema from schema.sql
     */
    private void initializeSchema() {
        try (Connection conn = getConnection()) {

            // Read schema.sql from resources
            InputStream schemaStream = plugin.getResource("schema.sql");
            if (schemaStream == null) {
                plugin.getLogger().warning("⚠️ schema.sql not found in resources!");
                return;
            }

            String schema;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(schemaStream, StandardCharsets.UTF_8))) {
                schema = reader.lines().collect(Collectors.joining("\n"));
            }

            // Remove SQL comments before splitting into statements
            String schemaWithoutComments = schema
                    .replaceAll("(?s)/\\*.*?\\*/", "")
                    .lines()
                    .filter(line -> !line.trim().startsWith("--"))
                    .collect(Collectors.joining("\n"));

            // Execute each statement in isolation
            for (String statement : schemaWithoutComments.split(";")) {
                String trimmed = statement.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }

                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(trimmed);
                    String suffix = trimmed.length() > LOG_SQL_PREVIEW_LENGTH ? "..." : "";
                    plugin.getLogger().fine("Executed: "
                            + trimmed.substring(0, Math.min(LOG_SQL_PREVIEW_LENGTH, trimmed.length())) + suffix);
                } catch (SQLException e) {
                    plugin.getLogger().warning("Schema statement failed: " + e.getMessage());
                }
            }

            plugin.getLogger().info("✅ Database schema initialized");

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "❌ Failed to initialize schema", e);
        }
    }

    /**
     * Close all connections and shutdown pool
     */
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("✅ Database connection pool closed");
        }
    }

    public DatabaseType getDatabaseType() {
        return databaseType;
    }
}
