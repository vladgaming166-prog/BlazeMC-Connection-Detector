package net.blazemc.connectiondetector.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class IdentityRecord {

    private final UUID uuid;
    private final UuidSource uuidSource;
    private final AccountType accountType;
    private final String lastKnownUsername;
    private final Instant firstSeen;
    private final Instant lastSeen;

    public IdentityRecord(
            UUID uuid,
            UuidSource uuidSource,
            AccountType accountType,
            String lastKnownUsername,
            Instant firstSeen,
            Instant lastSeen
    ) {
        this.uuid = Objects.requireNonNull(uuid, "uuid");
        this.uuidSource = Objects.requireNonNull(uuidSource, "uuidSource");
        this.accountType = Objects.requireNonNull(accountType, "accountType");
        this.lastKnownUsername = Objects.requireNonNull(lastKnownUsername, "lastKnownUsername");
        this.firstSeen = Objects.requireNonNull(firstSeen, "firstSeen");
        this.lastSeen = Objects.requireNonNull(lastSeen, "lastSeen");
    }

    public UUID uuid() {
        return uuid;
    }

    public UuidSource uuidSource() {
        return uuidSource;
    }

    public AccountType accountType() {
        return accountType;
    }

    public String lastKnownUsername() {
        return lastKnownUsername;
    }

    public Instant firstSeen() {
        return firstSeen;
    }

    public Instant lastSeen() {
        return lastSeen;
    }

    public IdentityRecord withLastSeen(String username, Instant seenAt) {
        return new IdentityRecord(uuid, uuidSource, accountType, username, firstSeen, seenAt);
    }
}
