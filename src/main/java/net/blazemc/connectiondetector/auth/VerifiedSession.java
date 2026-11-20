package net.blazemc.connectiondetector.auth;

import java.util.UUID;
import net.blazemc.connectiondetector.model.AccountType;
import net.blazemc.connectiondetector.model.AuthenticationResult;
import net.blazemc.connectiondetector.model.ConnectionState;
import net.blazemc.connectiondetector.model.UuidSource;

public final class VerifiedSession {

    private final String username;
    private final UUID uuid;
    private final AccountType accountType;
    private final AuthenticationResult authenticationResult;
    private final UuidSource uuidSource;
    private final boolean premiumRegistered;
    private final UUID backendForwardingUuid;
    private final UUID expectedMojangUuid;
    private final boolean onlineMode;
    private final boolean sessionVerified;
    private final ConnectionState connectionState;
    private final boolean autoDetected;

    public VerifiedSession(
            String username,
            UUID uuid,
            AccountType accountType,
            AuthenticationResult authenticationResult,
            UuidSource uuidSource,
            boolean premiumRegistered,
            UUID backendForwardingUuid,
            UUID expectedMojangUuid,
            boolean onlineMode,
            boolean sessionVerified,
            ConnectionState connectionState,
            boolean autoDetected
    ) {
        this.username = username;
        this.uuid = uuid;
        this.accountType = accountType;
        this.authenticationResult = authenticationResult;
        this.uuidSource = uuidSource;
        this.premiumRegistered = premiumRegistered;
        this.backendForwardingUuid = backendForwardingUuid;
        this.expectedMojangUuid = expectedMojangUuid;
        this.onlineMode = onlineMode;
        this.sessionVerified = sessionVerified;
        this.connectionState = connectionState;
        this.autoDetected = autoDetected;
    }

    public static VerifiedSession from(LoginSnapshot snapshot, LoginDecision decision) {
        return from(snapshot, decision, null);
    }

    public static VerifiedSession from(
            LoginSnapshot snapshot,
            LoginDecision decision,
            PendingAuthSession pending
    ) {
        ConnectionState state = decision.allowed()
                ? (decision.accountType() == AccountType.PREMIUM
                ? ConnectionState.PREMIUM_VERIFIED
                : ConnectionState.CRACKED_CONFIRMED)
                : ConnectionState.AUTHENTICATION_FAILED;
        return new VerifiedSession(
                snapshot.username(),
                snapshot.uuid(),
                decision.accountType(),
                decision.result(),
                decision.uuidSource(),
                decision.accountType() == AccountType.PREMIUM,
                decision.forwardingUuid(),
                pending == null ? null : pending.expectedMojangUuid(),
                snapshot.onlineMode(),
                snapshot.onlineMode() && snapshot.mojangSessionEvidence(),
                pending == null ? state : (decision.allowed() ? state : pending.connectionState()),
                pending != null && pending.autoDetected()
        );
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

    public UUID expectedMojangUuid() {
        return expectedMojangUuid;
    }

    public boolean onlineMode() {
        return onlineMode;
    }

    public boolean sessionVerified() {
        return sessionVerified;
    }

    public ConnectionState connectionState() {
        return connectionState;
    }

    public boolean autoDetected() {
        return autoDetected;
    }

    public boolean verifiedPremium() {
        return accountType == AccountType.PREMIUM
                && authenticationResult == AuthenticationResult.PREMIUM_VERIFIED;
    }
}
