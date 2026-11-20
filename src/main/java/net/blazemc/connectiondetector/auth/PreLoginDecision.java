package net.blazemc.connectiondetector.auth;

import java.util.Optional;
import java.util.UUID;
import net.blazemc.connectiondetector.model.AccountType;
import net.blazemc.connectiondetector.model.AuthenticationResult;
import net.blazemc.connectiondetector.model.ConnectionState;
import net.blazemc.connectiondetector.model.PremiumAccount;

public final class PreLoginDecision {

    public enum Action {
        FORCE_ONLINE,
        FORCE_OFFLINE,
        DENY
    }

    private final Action action;
    private final AccountType intendedType;
    private final ConnectionState connectionState;
    private final AuthenticationResult result;
    private final PremiumAccount premiumAccount;
    private final UUID expectedMojangUuid;
    private final String reasonCode;
    private final boolean autoDetected;

    private PreLoginDecision(
            Action action,
            AccountType intendedType,
            ConnectionState connectionState,
            AuthenticationResult result,
            PremiumAccount premiumAccount,
            UUID expectedMojangUuid,
            String reasonCode,
            boolean autoDetected
    ) {
        this.action = action;
        this.intendedType = intendedType;
        this.connectionState = connectionState;
        this.result = result;
        this.premiumAccount = premiumAccount;
        this.expectedMojangUuid = expectedMojangUuid;
        this.reasonCode = reasonCode;
        this.autoDetected = autoDetected;
    }

    public static PreLoginDecision forceOnline(PremiumAccount account) {
        return new PreLoginDecision(
                Action.FORCE_ONLINE,
                AccountType.PREMIUM,
                ConnectionState.PREMIUM_CANDIDATE,
                AuthenticationResult.PREMIUM_VERIFIED,
                account,
                account.mojangUuid(),
                "cached_premium",
                false
        );
    }

    public static PreLoginDecision forceOnlineCandidate(UUID expectedUuid, boolean autoDetected) {
        return new PreLoginDecision(
                Action.FORCE_ONLINE,
                AccountType.PREMIUM,
                ConnectionState.PREMIUM_CANDIDATE,
                AuthenticationResult.PREMIUM_VERIFIED,
                null,
                expectedUuid,
                autoDetected ? "automatic_premium" : "reserved_premium",
                autoDetected
        );
    }

    public static PreLoginDecision forceOffline() {
        return new PreLoginDecision(
                Action.FORCE_OFFLINE,
                AccountType.CRACKED,
                ConnectionState.CRACKED_CONFIRMED,
                AuthenticationResult.CRACKED_CONFIRMED,
                null,
                null,
                "cracked_confirmed",
                false
        );
    }

    public static PreLoginDecision deny(AuthenticationResult result, ConnectionState state, String reasonCode) {
        return new PreLoginDecision(Action.DENY, AccountType.UNKNOWN, state, result, null, null, reasonCode, false);
    }

    public Action action() {
        return action;
    }

    public AccountType intendedType() {
        return intendedType;
    }

    public ConnectionState connectionState() {
        return connectionState;
    }

    public AuthenticationResult result() {
        return result;
    }

    public Optional<PremiumAccount> premiumAccount() {
        return Optional.ofNullable(premiumAccount);
    }

    public Optional<UUID> expectedMojangUuid() {
        return Optional.ofNullable(expectedMojangUuid);
    }

    public String reasonCode() {
        return reasonCode;
    }

    public boolean autoDetected() {
        return autoDetected;
    }
}
