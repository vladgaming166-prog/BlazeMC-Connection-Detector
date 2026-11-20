package net.blazemc.connectiondetector.auth;

import java.util.UUID;
import net.blazemc.connectiondetector.model.AccountType;
import net.blazemc.connectiondetector.model.ConnectionState;

public final class PendingAuthSession {

    private final String connectionId;
    private final String username;
    private final AccountType intendedType;
    private final ConnectionState connectionState;
    private final UUID expectedMojangUuid;
    private final UUID clientClaimedUuid;
    private final boolean autoDetected;

    public PendingAuthSession(
            String connectionId,
            String username,
            AccountType intendedType,
            ConnectionState connectionState,
            UUID expectedMojangUuid,
            UUID clientClaimedUuid,
            boolean autoDetected
    ) {
        this.connectionId = connectionId;
        this.username = username;
        this.intendedType = intendedType;
        this.connectionState = connectionState;
        this.expectedMojangUuid = expectedMojangUuid;
        this.clientClaimedUuid = clientClaimedUuid;
        this.autoDetected = autoDetected;
    }

    public static PendingAuthSession from(String connectionId, String username, PreLoginDecision decision, UUID claimed) {
        return new PendingAuthSession(
                connectionId,
                username,
                decision.intendedType(),
                decision.connectionState(),
                decision.expectedMojangUuid().orElse(null),
                claimed,
                decision.autoDetected()
        );
    }

    public String connectionId() {
        return connectionId;
    }

    public String username() {
        return username;
    }

    public AccountType intendedType() {
        return intendedType;
    }

    public ConnectionState connectionState() {
        return connectionState;
    }

    public UUID expectedMojangUuid() {
        return expectedMojangUuid;
    }

    public UUID clientClaimedUuid() {
        return clientClaimedUuid;
    }

    public boolean autoDetected() {
        return autoDetected;
    }

    public boolean premiumCandidate() {
        return connectionState == ConnectionState.PREMIUM_CANDIDATE && expectedMojangUuid != null;
    }
}
