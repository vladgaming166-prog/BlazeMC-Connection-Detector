package net.blazemc.connectiondetector.auth;

public final class CachedMojangApi implements MojangApi {

    private final MojangApi delegate;
    private final MojangProfileCache cache;

    public CachedMojangApi(MojangApi delegate, MojangProfileCache cache) {
        this.delegate = delegate;
        this.cache = cache;
    }

    @Override
    public MojangLookupResult lookupByUsername(String username) {
        return cache.get(username).orElseGet(() -> {
            MojangLookupResult result = delegate.lookupByUsername(username);
            cache.put(username, result);
            return result;
        });
    }
}
