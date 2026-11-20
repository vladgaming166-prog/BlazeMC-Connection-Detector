package net.blazemc.connectiondetector.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

public final class ConfirmationStore {

    private static final Duration DEFAULT_TTL = Duration.ofSeconds(30);

    private final ConcurrentHashMap<String, Instant> pending = new ConcurrentHashMap<>();
    private final Duration ttl;

    public ConfirmationStore() {
        this(DEFAULT_TTL);
    }

    public ConfirmationStore(Duration ttl) {
        this.ttl = ttl;
    }

    public void arm(String key) {
        pending.put(key, Instant.now().plus(ttl));
    }

    public boolean consume(String key) {
        Instant existing = pending.remove(key);
        return existing != null && Instant.now().isBefore(existing);
    }

    public void clear(String key) {
        pending.remove(key);
    }
}
