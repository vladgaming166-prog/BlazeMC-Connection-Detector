package net.blazemc.connectiondetector.auth;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class CachedMojangApi implements MojangApi {

    private final MojangApi delegate;
    private final MojangProfileCache cache;
    private final ConcurrentHashMap<String, CompletableFuture<MojangLookupResult>> inFlight = new ConcurrentHashMap<>();

    public CachedMojangApi(MojangApi delegate, MojangProfileCache cache) {
        this.delegate = delegate;
        this.cache = cache;
    }

    public MojangProfileCache cache() {
        return cache;
    }

    @Override
    public MojangLookupResult lookupByUsername(String username) {
        return lookupByUsernameAsync(username).join();
    }

    @Override
    public CompletableFuture<MojangLookupResult> lookupByUsernameAsync(String username) {
        return cache.get(username).map(CompletableFuture::completedFuture)
                .orElseGet(() -> inFlight.computeIfAbsent(UsernameValidator.normalizeKey(username), key ->
                        delegate.lookupByUsernameAsync(username)
                                .whenComplete((result, error) -> {
                                    if (result != null) {
                                        cache.put(username, result);
                                    }
                                    inFlight.remove(key);
                                })));
    }

    @Override
    public void invalidate(String username) {
        cache.invalidate(username);
        inFlight.remove(UsernameValidator.normalizeKey(username));
        delegate.invalidate(username);
    }
}
