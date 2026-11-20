package net.blazemc.connectiondetector.auth;

import java.util.Objects;
import java.util.UUID;

public final class MojangProfile {

    private final String canonicalUsername;
    private final UUID uuid;

    public MojangProfile(String canonicalUsername, UUID uuid) {
        this.canonicalUsername = Objects.requireNonNull(canonicalUsername, "canonicalUsername");
        this.uuid = Objects.requireNonNull(uuid, "uuid");
    }

    public String canonicalUsername() {
        return canonicalUsername;
    }

    public UUID uuid() {
        return uuid;
    }
}
