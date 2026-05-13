package collectionlog.database;

import collectionlog.CollectionLogPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class DatabaseBackupManager {
    private static final DateTimeFormatter BACKUP_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");
    private static final DateTimeFormatter DISPLAY_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CollectionLogPlugin plugin;
    private final File backupDirectory;
    private final AtomicBoolean backupInProgress = new AtomicBoolean(false);
    private BukkitTask scheduledTask;
    private volatile Instant lastBackupTime;

    public DatabaseBackupManager(CollectionLogPlugin plugin) {
        this.plugin = plugin;
        this.backupDirectory = new File(plugin.getDataFolder(), "backups");
    }

    public void scheduleBackups() {
        int intervalMinutes = getBackupIntervalMinutes();
        if (intervalMinutes <= 0) {
            plugin.getLogger().info("Database backups are disabled (backup-interval <= 0)");
            return;
        }

        if (!backupDirectory.exists() && !backupDirectory.mkdirs()) {
            plugin.getLogger().warning("Failed to create backup directory: " + backupDirectory.getAbsolutePath());
            return;
        }

        long periodTicks = intervalMinutes * 60L * 20L;
        scheduledTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                () -> createBackup().exceptionally(ex -> {
                    plugin.getLogger().warning("Scheduled backup failed: " + ex.getMessage());
                    return null;
                }),
                periodTicks,
                periodTicks
        );
        plugin.getLogger().info("Scheduled database backups every " + intervalMinutes + " minute(s)");
    }

    public CompletableFuture<File> createBackup() {
        return plugin.getAsyncTaskManager().runAsync(() -> {
            if (!backupInProgress.compareAndSet(false, true)) {
                throw new IllegalStateException("A backup is already in progress.");
            }
            try {
                ensureBackupDirectoryExists();
                File backupFile = createBackupInternal();
                rotateBackupsInternal();
                lastBackupTime = Instant.now();
                return backupFile;
            } finally {
                backupInProgress.set(false);
            }
        });
    }

    public CompletableFuture<List<File>> listBackups() {
        return plugin.getAsyncTaskManager().runAsync(this::listBackupsInternal);
    }

    public CompletableFuture<Boolean> restoreBackup(File backupFile) {
        return plugin.getAsyncTaskManager().runAsync(() -> {
            if (!backupFile.exists() || !backupFile.isFile()) {
                return false;
            }

            DatabaseType databaseType = plugin.getDatabaseManager().getDatabaseType();
            if (databaseType == DatabaseType.SQLITE) {
                restoreSqliteBackup(backupFile);
            } else {
                restoreMysqlBackup(backupFile);
            }
            return true;
        });
    }

    public void rotateBackups() {
        plugin.getAsyncTaskManager().runAsync(() -> {
            try {
                rotateBackupsInternal();
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to rotate backups: " + e.getMessage());
            }
        });
    }

    public void shutdown() {
        if (scheduledTask != null) {
            scheduledTask.cancel();
            scheduledTask = null;
        }

        if (plugin.getConfig().getBoolean("database.backup-on-shutdown", true)) {
            try {
                createBackup().join();
            } catch (Exception exception) {
                plugin.getLogger().warning("Backup-on-shutdown failed: " + exception.getMessage());
            }
        }
    }

    public Instant getLastBackupTime() {
        return lastBackupTime;
    }

    public String getLastBackupDisplay() {
        if (lastBackupTime == null) {
            return "Never";
        }
        LocalDateTime localDateTime = LocalDateTime.ofInstant(lastBackupTime, ZoneId.systemDefault());
        return DISPLAY_TIMESTAMP.format(localDateTime);
    }

    public File getBackupDirectory() {
        return backupDirectory;
    }

    private int getBackupIntervalMinutes() {
        int value = plugin.getConfig().getInt("database.backup-interval", -1);
        if (value >= 0) {
            return value;
        }
        return plugin.getConfig().getInt("plugin.backup-interval", 30);
    }

    private int getBackupRetention() {
        return Math.max(1, plugin.getConfig().getInt("database.backup-retention", 10));
    }

    private void ensureBackupDirectoryExists() throws IOException {
        if (!backupDirectory.exists() && !backupDirectory.mkdirs()) {
            throw new IOException("Could not create backup directory at " + backupDirectory.getAbsolutePath());
        }
    }

    private File createBackupInternal() throws IOException, SQLException {
        DatabaseType databaseType = plugin.getDatabaseManager().getDatabaseType();
        String extension = databaseType == DatabaseType.SQLITE ? "db" : "sql";
        String fileName = "backup-" + BACKUP_TIMESTAMP.format(LocalDateTime.now()) + "." + extension;
        File backupFile = new File(backupDirectory, fileName);

        if (databaseType == DatabaseType.SQLITE) {
            File source = new File(plugin.getDataFolder(), "data.db");
            if (!source.exists()) {
                throw new IOException("SQLite database file not found: " + source.getAbsolutePath());
            }
            Files.copy(source.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } else {
            exportMysqlToFile(backupFile);
        }

        plugin.getLogger().info("Created database backup: " + backupFile.getName());
        return backupFile;
    }

    private List<File> listBackupsInternal() {
        if (!backupDirectory.exists() || !backupDirectory.isDirectory()) {
            return List.of();
        }
        File[] files = backupDirectory.listFiles(File::isFile);
        if (files == null || files.length == 0) {
            return List.of();
        }
        List<File> backups = new ArrayList<>(List.of(files));
        backups.sort(Comparator.comparingLong(File::lastModified).reversed());
        return backups;
    }

    private void rotateBackupsInternal() throws IOException {
        List<File> backups = listBackupsInternal();
        int retention = getBackupRetention();
        if (backups.size() <= retention) {
            return;
        }

        for (int i = retention; i < backups.size(); i++) {
            File file = backups.get(i);
            Files.deleteIfExists(file.toPath());
            plugin.getLogger().info("Deleted old backup: " + file.getName());
        }
    }

    private void restoreSqliteBackup(File backupFile) throws IOException {
        File databaseFile = new File(plugin.getDataFolder(), "data.db");
        if (!databaseFile.exists()) {
            Files.copy(backupFile.toPath(), databaseFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return;
        }

        File safetyBackup = new File(plugin.getDataFolder(),
                "data-before-restore-" + BACKUP_TIMESTAMP.format(LocalDateTime.now()) + ".db");
        Files.copy(databaseFile.toPath(), safetyBackup.toPath(), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(backupFile.toPath(), databaseFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private void restoreMysqlBackup(File backupFile) throws IOException, SQLException {
        List<String> statements = splitSqlStatements(Files.readString(backupFile.toPath(), StandardCharsets.UTF_8));
        try (Connection connection = plugin.getDatabaseManager().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("SET FOREIGN_KEY_CHECKS=0");
            for (String sql : statements) {
                statement.execute(sql);
            }
            statement.execute("SET FOREIGN_KEY_CHECKS=1");
        }
    }

    private void exportMysqlToFile(File outputFile) throws SQLException, IOException {
        try (Connection connection = plugin.getDatabaseManager().getConnection();
             BufferedWriter writer = Files.newBufferedWriter(outputFile.toPath(), StandardCharsets.UTF_8)) {
            DatabaseMetaData databaseMetaData = connection.getMetaData();
            String databaseName = connection.getCatalog();

            List<String> tableNames = new ArrayList<>();
            try (ResultSet tables = databaseMetaData.getTables(databaseName, null, "%", new String[]{"TABLE"})) {
                while (tables.next()) {
                    String tableName = tables.getString("TABLE_NAME");
                    if (tableName != null) {
                        tableNames.add(tableName);
                    }
                }
            }

            for (String tableName : tableNames) {
                writeCreateTable(writer, connection, tableName);
                writeTableRows(writer, connection, tableName);
                writer.newLine();
            }
        }
    }

    private void writeCreateTable(BufferedWriter writer, Connection connection, String tableName) throws SQLException, IOException {
        try (PreparedStatement statement = connection.prepareStatement("SHOW CREATE TABLE `" + tableName + "`");
             ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                return;
            }
            String createStatement = resultSet.getString(2);
            writer.write("DROP TABLE IF EXISTS `" + tableName + "`;");
            writer.newLine();
            writer.write(createStatement + ";");
            writer.newLine();
        }
    }

    private void writeTableRows(BufferedWriter writer, Connection connection, String tableName) throws SQLException, IOException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM `" + tableName + "`");
             ResultSet resultSet = statement.executeQuery()) {
            ResultSetMetaData metadata = resultSet.getMetaData();
            int columns = metadata.getColumnCount();
            while (resultSet.next()) {
                writer.write("INSERT INTO `" + tableName + "` VALUES (");
                for (int index = 1; index <= columns; index++) {
                    Object value = resultSet.getObject(index);
                    if (value == null) {
                        writer.write("NULL");
                    } else if (value instanceof Number) {
                        writer.write(value.toString());
                    } else if (value instanceof Boolean bool) {
                        writer.write(bool ? "1" : "0");
                    } else if (value instanceof byte[] bytes) {
                        writer.write("x'" + toHex(bytes) + "'");
                    } else {
                        writer.write("'" + escapeSql(value.toString()) + "'");
                    }

                    if (index < columns) {
                        writer.write(", ");
                    }
                }
                writer.write(");");
                writer.newLine();
            }
        }
    }

    private List<String> splitSqlStatements(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        for (int i = 0; i < sql.length(); i++) {
            char character = sql.charAt(i);
            if (character == '\'') {
                if (inSingleQuote && i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                    current.append(character);
                    current.append(sql.charAt(i + 1));
                    i++;
                    continue;
                }
                if (i == 0 || sql.charAt(i - 1) != '\\') {
                    inSingleQuote = !inSingleQuote;
                }
            }
            if (character == ';' && !inSingleQuote) {
                String statement = current.toString().trim();
                if (!statement.isEmpty()) {
                    statements.add(statement);
                }
                current.setLength(0);
                continue;
            }
            current.append(character);
        }
        String trailing = current.toString().trim();
        if (!trailing.isEmpty()) {
            statements.add(trailing);
        }
        return statements;
    }

    private String escapeSql(String value) {
        return value.replace("\\", "\\\\").replace("'", "''");
    }

    private String toHex(byte[] data) {
        StringBuilder result = new StringBuilder(data.length * 2);
        for (byte b : data) {
            result.append(String.format(Locale.ROOT, "%02X", b));
        }
        return result.toString();
    }
}
