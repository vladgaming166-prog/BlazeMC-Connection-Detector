package net.blazemc.connectiondetector.auth;

import java.util.UUID;
import net.blazemc.connectiondetector.model.AccountType;
import net.blazemc.connectiondetector.model.AuthenticationResult;
import net.blazemc.connectiondetector.model.UuidSource;

public final class VerifiedSession {

    private final String username;
    private final UUID uuid;
    private final AccountType accountType;
    private final AuthenticationResult authenticationResult;
    private final UuidSource uuidSource;
    private final boolean premiumRegistered;
    private final UUID backendForwardingUuid;

    public VerifiedSession(
            String username,
            UUID uuid,
            AccountType accountType,
            AuthenticationResult authenticationResult,
            UuidSource uuidSource,
            boolean premiumRegistered,
            UUID backendForwardingUuid
    ) {
        this.username = username;
        this.uuid = uuid;
        this.accountType = accountType;
        this.authenticationResult = authenticationResult;
        this.uuidSource = uuidSource;
        this.premiumRegistered = premiumRegistered;
        this.backendForwardingUuid = backendForwardingUuid;
    }

    public static VerifiedSession from(LoginSnapshot snapshot, LoginDecision decision) {
        return new VerifiedSession(
                snapshot.username(),
                snapshot.uuid(),
                decision.accountType(),
                decision.result(),
                decision.uuidSource(),
                decision.premiumAccount() != null,
                decision.forwardingUuid()
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

    public boolean verifiedPremium() {
        return accountType == AccountType.PREMIUM
                && authenticationResult == AuthenticationResult.PREMIUM_VERIFIED;
    }
}
