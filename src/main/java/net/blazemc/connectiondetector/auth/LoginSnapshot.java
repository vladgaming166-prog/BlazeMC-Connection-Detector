package net.blazemc.connectiondetector.auth;

import java.util.UUID;

public final class LoginSnapshot {

    private final String username;
    private final UUID uuid;
    private final boolean onlineMode;
    private final boolean mojangSessionEvidence;
    private final UUID clientClaimedUuid;

    public LoginSnapshot(
            String username,
            UUID uuid,
            boolean onlineMode,
            boolean mojangSessionEvidence,
            UUID clientClaimedUuid
    ) {
        this.username = username;
        this.uuid = uuid;
        this.onlineMode = onlineMode;
        this.mojangSessionEvidence = mojangSessionEvidence;
        this.clientClaimedUuid = clientClaimedUuid;
    }

    public String username() {
        return username;
    }

    public UUID uuid() {
        return uuid;
    }

    public boolean onlineMode() {
        return onlineMode;
    }

    public boolean mojangSessionEvidence() {
        return mojangSessionEvidence;
    }

    public UUID clientClaimedUuid() {
        return clientClaimedUuid;
    }
}
