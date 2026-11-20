package net.blazemc.connectiondetector.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class MojangProfileCache {

    private final ConcurrentHashMap<String, CacheEntry> entries = new ConcurrentHashMap<>();
    private final Duration ttl;

    public MojangProfileCache(Duration ttl) {
        this.ttl = ttl;
    }

    public Optional<MojangLookupResult> get(String username) {
        if (ttl.isZero() || ttl.isNegative()) {
            return Optional.empty();
        }
        CacheEntry entry = entries.get(UsernameValidator.normalizeKey(username));
        if (entry == null || Instant.now().isAfter(entry.expiresAt())) {
            if (entry != null) {
                entries.remove(UsernameValidator.normalizeKey(username), entry);
            }
            return Optional.empty();
        }
        return Optional.of(entry.result());
    }

    public void put(String username, MojangLookupResult result) {
        if (ttl.isZero() || ttl.isNegative()) {
            return;
        }
        if (result.isUnavailable()) {
            return;
        }
        entries.put(
                UsernameValidator.normalizeKey(username),
                new CacheEntry(result, Instant.now().plus(ttl))
        );
    }

    public void clear() {
        entries.clear();
    }

    private record CacheEntry(MojangLookupResult result, Instant expiresAt) {
    }
}
