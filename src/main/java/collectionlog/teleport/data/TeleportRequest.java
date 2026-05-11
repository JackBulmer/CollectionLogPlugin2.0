package collectionlog.teleport.data;

import java.util.UUID;

public class TeleportRequest {
    private final UUID requesterId;
    private final UUID targetId;
    private final long createdAt;

    public TeleportRequest(UUID requesterId, UUID targetId) {
        this.requesterId = requesterId;
        this.targetId = targetId;
        this.createdAt = System.currentTimeMillis();
    }

    public UUID getRequesterId() { return requesterId; }
    public UUID getTargetId() { return targetId; }
    public long getCreatedAt() { return createdAt; }

    public boolean isExpired(long timeoutMillis) {
        return System.currentTimeMillis() - createdAt > timeoutMillis;
    }
}
