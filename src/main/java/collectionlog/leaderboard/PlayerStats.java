package collectionlog.leaderboard;

import java.time.Instant;
import java.util.UUID;

public record PlayerStats(
        UUID playerId,
        String username,
        int totalCollected,
        Instant lastUpdated
) {
}
