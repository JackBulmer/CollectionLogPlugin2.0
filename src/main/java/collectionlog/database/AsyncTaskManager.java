package collectionlog.database;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.*;
import java.util.logging.Level;

public class AsyncTaskManager {
    private final Plugin plugin;
    private final ExecutorService executor;

    public AsyncTaskManager(Plugin plugin) {
        this.plugin = plugin;
        this.executor = Executors.newFixedThreadPool(
                4,
                r -> {
                    Thread thread = new Thread(r);
                    thread.setName("CollectionLog-Async");
                    thread.setDaemon(true);
                    return thread;
                }
        );
    }

    /**
     * Run a database task asynchronously
     */
    public <T> CompletableFuture<T> runAsync(Callable<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();

        executor.submit(() -> {
            try {
                T result = task.call();
                future.complete(result);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Async task failed", e);
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    /**
     * Run a task asynchronously without return value
     */
    public CompletableFuture<Void> runAsync(Runnable task) {
        return CompletableFuture.runAsync(task, executor)
                .exceptionally(throwable -> {
                    plugin.getLogger().log(Level.SEVERE, "Async task failed", throwable);
                    return null;
                });
    }

    /**
     * Run a task synchronously on the main thread
     */
    public void runSync(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /**
     * Shutdown executor
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
            plugin.getLogger().info("✅ Async task manager shutdown");
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
