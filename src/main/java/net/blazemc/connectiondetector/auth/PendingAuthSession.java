package net.blazemc.connectiondetector.auth;

import java.util.UUID;
import net.blazemc.connectiondetector.model.AccountType;

public final class PendingAuthSession {

    private final String connectionId;
    private final String username;
    private final AccountType intendedType;
    private final UUID expectedMojangUuid;
    private final UUID clientClaimedUuid;

    public PendingAuthSession(
            String connectionId,
            String username,
            AccountType intendedType,
            UUID expectedMojangUuid,
            UUID clientClaimedUuid
    ) {
        this.connectionId = connectionId;
        this.username = username;
        this.intendedType = intendedType;
        this.expectedMojangUuid = expectedMojangUuid;
        this.clientClaimedUuid = clientClaimedUuid;
    }

    public static PendingAuthSession from(String connectionId, String username, PreLoginDecision decision, UUID claimed) {
        return new PendingAuthSession(
                connectionId,
                username,
                decision.intendedType(),
                decision.expectedMojangUuid().orElse(null),
                claimed
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

    public UUID expectedMojangUuid() {
        return expectedMojangUuid;
    }

    public UUID clientClaimedUuid() {
        return clientClaimedUuid;
    }

    public boolean reservedPremium() {
        return intendedType == AccountType.PREMIUM && expectedMojangUuid != null;
    }

}
