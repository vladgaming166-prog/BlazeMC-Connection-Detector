package net.blazemc.connectiondetector.auth;

import java.util.concurrent.CompletableFuture;

public interface MojangApi {

    default MojangLookupResult lookupByUsername(String username) {
        return lookupByUsernameAsync(username).join();
    }

    CompletableFuture<MojangLookupResult> lookupByUsernameAsync(String username);

    default void invalidate(String username) {
    }
}
