package net.blazemc.connectiondetector.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

public final class ConcurrentLoginGuard {

    private static final Duration DEFAULT_TTL = Duration.ofSeconds(30);

    private final ConcurrentHashMap<String, Hold> inFlight = new ConcurrentHashMap<>();
    private final Duration ttl;

    public ConcurrentLoginGuard() {
        this(DEFAULT_TTL);
    }

    public ConcurrentLoginGuard(Duration ttl) {
        this.ttl = ttl;
    }

    public boolean tryAcquire(String username, String connectionId) {
        if (username == null || connectionId == null) {
            return false;
        }
        String key = UsernameValidator.normalizeKey(username);
        Instant now = Instant.now();
        inFlight.compute(key, (ignored, existing) -> {
            if (existing == null || now.isAfter(existing.expiresAt()) || existing.connectionId().equals(connectionId)) {
                return new Hold(connectionId, now.plus(ttl));
            }
            return existing;
        });
        Hold current = inFlight.get(key);
        return current != null && current.connectionId().equals(connectionId);
    }

    public void release(String username, String connectionId) {
        if (username == null || connectionId == null) {
            return;
        }
        String key = UsernameValidator.normalizeKey(username);
        inFlight.computeIfPresent(key, (ignored, existing) ->
                existing.connectionId().equals(connectionId) ? null : existing);
    }

    public boolean isHeld(String username) {
        Hold hold = inFlight.get(UsernameValidator.normalizeKey(username));
        return hold != null && Instant.now().isBefore(hold.expiresAt());
    }

    public void clear() {
        inFlight.clear();
    }

    public int size() {
        return inFlight.size();
    }

    private record Hold(String connectionId, Instant expiresAt) {
    }
}
