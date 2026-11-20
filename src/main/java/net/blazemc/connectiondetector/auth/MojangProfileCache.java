package net.blazemc.connectiondetector.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class MojangProfileCache {

    private final ConcurrentHashMap<String, CacheEntry> entries = new ConcurrentHashMap<>();
    private volatile Duration positiveTtl;
    private volatile Duration negativeTtl;

    public MojangProfileCache(Duration positiveTtl, Duration negativeTtl) {
        this.positiveTtl = positiveTtl;
        this.negativeTtl = negativeTtl;
    }

    public void updateTtl(Duration positiveTtl, Duration negativeTtl) {
        this.positiveTtl = positiveTtl;
        this.negativeTtl = negativeTtl;
    }

    public Optional<MojangLookupResult> get(String username) {
        String key = UsernameValidator.normalizeKey(username);
        CacheEntry entry = entries.get(key);
        if (entry == null || Instant.now().isAfter(entry.expiresAt())) {
            if (entry != null) {
                entries.remove(key, entry);
            }
            return Optional.empty();
        }
        return Optional.of(entry.result());
    }

    public void put(String username, MojangLookupResult result) {
        if (result.isUnavailable()) {
            return;
        }
        Duration ttl = result.status() == MojangLookupResult.Status.NOT_FOUND ? negativeTtl : positiveTtl;
        if (ttl.isZero() || ttl.isNegative()) {
            return;
        }
        entries.put(
                UsernameValidator.normalizeKey(username),
                new CacheEntry(result, Instant.now().plus(ttl))
        );
        if (result.profile().isPresent()) {
            entries.put(
                    UsernameValidator.normalizeKey(result.profile().get().canonicalUsername()),
                    new CacheEntry(result, Instant.now().plus(ttl))
            );
        }
    }

    public void invalidate(String username) {
        String key = UsernameValidator.normalizeKey(username);
        CacheEntry removed = entries.remove(key);
        if (removed != null && removed.result().profile().isPresent()) {
            entries.remove(UsernameValidator.normalizeKey(removed.result().profile().get().canonicalUsername()));
        }
    }

    public void clear() {
        entries.clear();
    }

    private record CacheEntry(MojangLookupResult result, Instant expiresAt) {
    }
}
