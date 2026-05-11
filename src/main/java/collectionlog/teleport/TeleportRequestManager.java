package collectionlog.teleport;

import collectionlog.CollectionLogPlugin;
import collectionlog.teleport.data.TeleportRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TeleportRequestManager {
    private final CollectionLogPlugin plugin;
    // targetId -> list of pending requests
    private final Map<UUID, List<TeleportRequest>> pendingRequests = new ConcurrentHashMap<>();

    public TeleportRequestManager(CollectionLogPlugin plugin) {
        this.plugin = plugin;
    }

    public void addRequest(UUID requesterId, UUID targetId) {
        pendingRequests.computeIfAbsent(targetId, k -> new ArrayList<>())
            .removeIf(r -> r.getRequesterId().equals(requesterId));
        pendingRequests.computeIfAbsent(targetId, k -> new ArrayList<>())
            .add(new TeleportRequest(requesterId, targetId));
    }

    public List<TeleportRequest> getPendingRequests(UUID targetId) {
        long timeout = plugin.getConfig().getLong("teleport.request_timeout", 60) * 1000L;
        List<TeleportRequest> requests = pendingRequests.getOrDefault(targetId, new ArrayList<>());
        requests.removeIf(r -> r.isExpired(timeout));
        return new ArrayList<>(requests);
    }

    public TeleportRequest getRequest(UUID requesterId, UUID targetId) {
        long timeout = plugin.getConfig().getLong("teleport.request_timeout", 60) * 1000L;
        List<TeleportRequest> requests = pendingRequests.getOrDefault(targetId, new ArrayList<>());
        return requests.stream()
            .filter(r -> r.getRequesterId().equals(requesterId) && !r.isExpired(timeout))
            .findFirst().orElse(null);
    }

    public void removeRequest(UUID requesterId, UUID targetId) {
        List<TeleportRequest> requests = pendingRequests.get(targetId);
        if (requests != null) {
            requests.removeIf(r -> r.getRequesterId().equals(requesterId));
            if (requests.isEmpty()) pendingRequests.remove(targetId);
        }
    }

    public void removeAllRequestsFrom(UUID requesterId) {
        for (List<TeleportRequest> requests : pendingRequests.values()) {
            requests.removeIf(r -> r.getRequesterId().equals(requesterId));
        }
        pendingRequests.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    public void removeAllRequestsTo(UUID targetId) {
        pendingRequests.remove(targetId);
    }
}
