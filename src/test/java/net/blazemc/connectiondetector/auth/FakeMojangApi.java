package net.blazemc.connectiondetector.auth;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class FakeMojangApi implements MojangApi {

    private final Map<String, MojangLookupResult> responses = new ConcurrentHashMap<>();
    private final AtomicInteger calls = new AtomicInteger();
    private MojangLookupResult fallback = MojangLookupResult.notFound();

    public FakeMojangApi put(String username, MojangProfile profile) {
        responses.put(UsernameValidator.normalizeKey(username), MojangLookupResult.found(profile));
        return this;
    }

    public FakeMojangApi put(String username, MojangLookupResult result) {
        responses.put(UsernameValidator.normalizeKey(username), result);
        return this;
    }

    public FakeMojangApi fallback(MojangLookupResult fallback) {
        this.fallback = fallback;
        return this;
    }

    public int calls() {
        return calls.get();
    }

    @Override
    public MojangLookupResult lookupByUsername(String username) {
        calls.incrementAndGet();
        return responses.getOrDefault(UsernameValidator.normalizeKey(username), fallback);
    }

    public static MojangProfile profile(String name, String uuid) {
        return new MojangProfile(name, UUID.fromString(uuid));
    }
}
