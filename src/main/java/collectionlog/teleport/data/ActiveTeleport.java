package collectionlog.teleport.data;

import org.bukkit.Location;

import java.util.UUID;
import java.util.function.Consumer;

public class ActiveTeleport {
    private final UUID playerId;
    private final Location startLocation;
    private final int totalSeconds;
    private int remainingSeconds;
    private final Runnable onComplete;
    private final Consumer<String> onCancel;
    private int taskId = -1;

    public ActiveTeleport(UUID playerId, Location startLocation, int totalSeconds,
                          Runnable onComplete, Consumer<String> onCancel) {
        this.playerId = playerId;
        this.startLocation = startLocation;
        this.totalSeconds = totalSeconds;
        this.remainingSeconds = totalSeconds;
        this.onComplete = onComplete;
        this.onCancel = onCancel;
    }

    public UUID getPlayerId() { return playerId; }
    public Location getStartLocation() { return startLocation; }
    public int getTotalSeconds() { return totalSeconds; }
    public int getRemainingSeconds() { return remainingSeconds; }
    public void decrementSeconds() { remainingSeconds--; }
    public Runnable getOnComplete() { return onComplete; }
    public Consumer<String> getOnCancel() { return onCancel; }
    public int getTaskId() { return taskId; }
    public void setTaskId(int taskId) { this.taskId = taskId; }
}
