package net.blazemc.connectiondetector.auth;

import java.util.UUID;

public final class ProfileSnapshot {

    private final String username;
    private final UUID originalUuid;
    private final UUID currentUuid;
    private final boolean onlineMode;

    public ProfileSnapshot(String username, UUID originalUuid, UUID currentUuid, boolean onlineMode) {
        this.username = username;
        this.originalUuid = originalUuid;
        this.currentUuid = currentUuid;
        this.onlineMode = onlineMode;
    }

    public String username() {
        return username;
    }

    public UUID originalUuid() {
        return originalUuid;
    }

    public UUID currentUuid() {
        return currentUuid;
    }

    public boolean onlineMode() {
        return onlineMode;
    }
}
