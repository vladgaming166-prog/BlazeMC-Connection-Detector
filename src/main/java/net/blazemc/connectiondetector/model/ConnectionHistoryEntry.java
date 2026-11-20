package net.blazemc.connectiondetector.model;

import java.time.Instant;
import java.util.UUID;

public final class ConnectionHistoryEntry {

    private final String username;
    private final UUID uuid;
    private final AccountType accountType;
    private final AuthenticationResult authenticationResult;
    private final UuidSource uuidSource;
    private final boolean premiumRegistered;
    private final UUID backendForwardingUuid;
    private final Instant connectedAt;
    private final String notes;

    public ConnectionHistoryEntry(
            String username,
            UUID uuid,
            AccountType accountType,
            AuthenticationResult authenticationResult,
            UuidSource uuidSource,
            boolean premiumRegistered,
            UUID backendForwardingUuid,
            Instant connectedAt,
            String notes
    ) {
        this.username = username;
        this.uuid = uuid;
        this.accountType = accountType;
        this.authenticationResult = authenticationResult;
        this.uuidSource = uuidSource;
        this.premiumRegistered = premiumRegistered;
        this.backendForwardingUuid = backendForwardingUuid;
        this.connectedAt = connectedAt;
        this.notes = notes;
    }

    public String username() {
        return username;
    }

    public UUID uuid() {
        return uuid;
    }

    public AccountType accountType() {
        return accountType;
    }

    public AuthenticationResult authenticationResult() {
        return authenticationResult;
    }

    public UuidSource uuidSource() {
        return uuidSource;
    }

    public boolean premiumRegistered() {
        return premiumRegistered;
    }

    public UUID backendForwardingUuid() {
        return backendForwardingUuid;
    }

    public Instant connectedAt() {
        return connectedAt;
    }

    public String notes() {
        return notes;
    }
}
