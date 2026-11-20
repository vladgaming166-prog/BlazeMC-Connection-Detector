package net.blazemc.connectiondetector.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MojangProfileCacheTest {

    @Test
    void positiveProfilesAreCachedAndNegativeCacheCanBeDisabled() {
        MojangProfileCache cache = new MojangProfileCache(Duration.ofMinutes(5), Duration.ZERO);
        MojangProfile profile = new MojangProfile("Blaz3Craft", UUID.fromString("11111111-1111-4111-8111-111111111111"));
        cache.put("Blaz3Craft", MojangLookupResult.found(profile));
        cache.put("CrackedGuy", MojangLookupResult.notFound());
        cache.put("Outage", MojangLookupResult.error("503", 503));

        assertTrue(cache.get("blaz3craft").isPresent());
        assertTrue(cache.get("CrackedGuy").isEmpty());
        assertTrue(cache.get("Outage").isEmpty());
    }

    @Test
    void invalidateRemovesCachedProfile() {
        MojangProfileCache cache = new MojangProfileCache(Duration.ofMinutes(5), Duration.ofMinutes(1));
        cache.put("Blaz3Craft", MojangLookupResult.found(
                new MojangProfile("Blaz3Craft", UUID.fromString("11111111-1111-4111-8111-111111111111"))));
        cache.invalidate("Blaz3Craft");
        assertTrue(cache.get("Blaz3Craft").isEmpty());
    }

    @Test
    void inFlightLookupsAreDeduplicated() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        MojangApi slow = username -> {
            calls.incrementAndGet();
            started.countDown();
            return CompletableFuture.supplyAsync(() -> {
                try {
                    release.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                return MojangLookupResult.found(new MojangProfile(
                        "Blaz3Craft", UUID.fromString("11111111-1111-4111-8111-111111111111")));
            });
        };
        CachedMojangApi cached = new CachedMojangApi(slow, new MojangProfileCache(Duration.ofMinutes(1), Duration.ZERO));
        CompletableFuture<MojangLookupResult> first = cached.lookupByUsernameAsync("Blaz3Craft");
        assertTrue(started.await(2, TimeUnit.SECONDS));
        CompletableFuture<MojangLookupResult> second = cached.lookupByUsernameAsync("blaz3craft");
        release.countDown();
        assertEquals(MojangLookupResult.Status.FOUND, first.get(2, TimeUnit.SECONDS).status());
        assertEquals(MojangLookupResult.Status.FOUND, second.get(2, TimeUnit.SECONDS).status());
        assertEquals(1, calls.get());
    }
}
