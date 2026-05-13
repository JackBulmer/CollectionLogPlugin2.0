package collectionlog.commands;

import collectionlog.CollectionLogPlugin;
import collectionlog.database.DatabaseBackupManager;
import collectionlog.database.DatabaseType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.io.File;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class DatabaseCommand implements CommandExecutor, TabCompleter {
    private static final DecimalFormat SIZE_FORMAT = new DecimalFormat("0.00");
    private static final DateTimeFormatter LIST_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CollectionLogPlugin plugin;
    private final DatabaseBackupManager backupManager;

    public DatabaseCommand(CollectionLogPlugin plugin, DatabaseBackupManager backupManager) {
        this.plugin = plugin;
        this.backupManager = backupManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("collectionlog.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§cUsage: /cldb <info|backup|list|restore>");
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "backup" -> handleBackup(sender);
            case "list" -> handleList(sender);
            case "restore" -> handleRestore(sender, args);
            case "info" -> handleInfo(sender);
            default -> {
                sender.sendMessage("§cUsage: /cldb <info|backup|list|restore>");
                yield true;
            }
        };
    }

    private boolean handleBackup(CommandSender sender) {
        sender.sendMessage("§eCreating database backup...");
        backupManager.createBackup()
                .thenAccept(file -> plugin.getAsyncTaskManager().runSync(() ->
                        sender.sendMessage("§aBackup created: §e" + file.getName())))
                .exceptionally(ex -> {
                    plugin.getLogger().warning("Manual backup failed: " + ex.getMessage());
                    plugin.getAsyncTaskManager().runSync(() ->
                            sender.sendMessage("§cFailed to create backup: " + ex.getMessage()));
                    return null;
                });
        return true;
    }

    private boolean handleList(CommandSender sender) {
        backupManager.listBackups()
                .thenAccept(backups -> plugin.getAsyncTaskManager().runSync(() -> {
                    if (backups.isEmpty()) {
                        sender.sendMessage("§eNo backups found.");
                        return;
                    }

                    sender.sendMessage("§6Available backups (§e" + backups.size() + "§6):");
                    backups.stream()
                            .sorted(Comparator.comparingLong(File::lastModified).reversed())
                            .forEach(file -> sender.sendMessage("§7- §e" + file.getName()
                                    + " §7(" + readableSize(file.length()) + ", "
                                    + LIST_TIME.format(Instant.ofEpochMilli(file.lastModified()).atZone(ZoneId.systemDefault()))
                                    + ")"));
                }))
                .exceptionally(ex -> {
                    plugin.getAsyncTaskManager().runSync(() -> sender.sendMessage("§cFailed to list backups."));
                    return null;
                });
        return true;
    }

    private boolean handleRestore(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /cldb restore <filename> [confirm]");
            return true;
        }

        if (args.length < 3 || !"confirm".equalsIgnoreCase(args[2])) {
            sender.sendMessage("§cRestore is destructive. Run:");
            sender.sendMessage("§e/cldb restore " + args[1] + " confirm");
            return true;
        }

        File backupFile = resolveBackup(args[1]);
        if (backupFile == null || !backupFile.exists()) {
            sender.sendMessage("§cBackup file not found.");
            return true;
        }

        sender.sendMessage("§eRestoring backup §6" + backupFile.getName() + "§e...");
        backupManager.restoreBackup(backupFile)
                .thenAccept(success -> plugin.getAsyncTaskManager().runSync(() -> {
                    if (!success) {
                        sender.sendMessage("§cFailed to restore backup.");
                        return;
                    }
                    sender.sendMessage("§aBackup restored. §eRestart the server immediately to ensure all data is reloaded.");
                }))
                .exceptionally(ex -> {
                    plugin.getAsyncTaskManager().runSync(() ->
                            sender.sendMessage("§cRestore failed: " + ex.getMessage()));
                    return null;
                });
        return true;
    }

    private boolean handleInfo(CommandSender sender) {
        DatabaseType type = plugin.getDatabaseManager().getDatabaseType();
        long dbSize = getDatabaseSize(type);
        sender.sendMessage("§6Database Info:");
        sender.sendMessage("§7Type: §e" + type.name().toLowerCase(Locale.ROOT));
        sender.sendMessage("§7Size: §e" + (dbSize >= 0 ? readableSize(dbSize) : "N/A"));
        sender.sendMessage("§7Backup directory: §e" + backupManager.getBackupDirectory().getAbsolutePath());
        sender.sendMessage("§7Last backup: §e" + backupManager.getLastBackupDisplay());
        return true;
    }

    private long getDatabaseSize(DatabaseType type) {
        if (type != DatabaseType.SQLITE) {
            return -1;
        }
        File dbFile = new File(plugin.getDataFolder(), "data.db");
        return dbFile.exists() ? dbFile.length() : 0L;
    }

    private File resolveBackup(String fileName) {
        File file = new File(backupManager.getBackupDirectory(), fileName);
        try {
            String parentPath = backupManager.getBackupDirectory().getCanonicalPath();
            String candidatePath = file.getCanonicalPath();
            if (!candidatePath.startsWith(parentPath + File.separator)) {
                return null;
            }
            return file;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String readableSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return SIZE_FORMAT.format(bytes / 1024.0) + " KB";
        }
        if (bytes < 1024L * 1024L * 1024L) {
            return SIZE_FORMAT.format(bytes / (1024.0 * 1024.0)) + " MB";
        }
        return SIZE_FORMAT.format(bytes / (1024.0 * 1024.0 * 1024.0)) + " GB";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase(Locale.ROOT);
            List<String> options = List.of("info", "backup", "list", "restore");
            return options.stream().filter(option -> option.startsWith(partial)).collect(Collectors.toList());
        }

        if (args.length == 2 && "restore".equalsIgnoreCase(args[0])) {
            String partial = args[1].toLowerCase(Locale.ROOT);
            return backupManager.listBackupsSync()
                    .stream()
                    .map(File::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(partial))
                    .limit(20)
                    .collect(Collectors.toList());
        }

        if (args.length == 3 && "restore".equalsIgnoreCase(args[0])) {
            String partial = args[2].toLowerCase(Locale.ROOT);
            if ("confirm".startsWith(partial)) {
                return List.of("confirm");
            }
        }

        return new ArrayList<>();
    }
}
