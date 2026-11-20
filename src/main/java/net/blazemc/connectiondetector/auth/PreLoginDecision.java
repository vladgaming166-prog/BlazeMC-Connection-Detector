package net.blazemc.connectiondetector.auth;

import java.util.Optional;
import java.util.UUID;
import net.blazemc.connectiondetector.model.AccountType;
import net.blazemc.connectiondetector.model.AuthenticationResult;
import net.blazemc.connectiondetector.model.PremiumAccount;

public final class PreLoginDecision {

    public enum Action {
        FORCE_ONLINE,
        FORCE_OFFLINE,
        DENY
    }

    private final Action action;
    private final AccountType intendedType;
    private final AuthenticationResult result;
    private final PremiumAccount premiumAccount;
    private final String reasonCode;

    private PreLoginDecision(
            Action action,
            AccountType intendedType,
            AuthenticationResult result,
            PremiumAccount premiumAccount,
            String reasonCode
    ) {
        this.action = action;
        this.intendedType = intendedType;
        this.result = result;
        this.premiumAccount = premiumAccount;
        this.reasonCode = reasonCode;
    }

    public static PreLoginDecision forceOnline(PremiumAccount account) {
        return new PreLoginDecision(
                Action.FORCE_ONLINE,
                AccountType.PREMIUM,
                AuthenticationResult.PREMIUM_VERIFIED,
                account,
                "force_online"
        );
    }

    public static PreLoginDecision forceOffline() {
        return new PreLoginDecision(
                Action.FORCE_OFFLINE,
                AccountType.CRACKED,
                AuthenticationResult.CRACKED_ALLOWED,
                null,
                "force_offline"
        );
    }

    public static PreLoginDecision deny(AuthenticationResult result, String reasonCode) {
        return new PreLoginDecision(Action.DENY, AccountType.UNKNOWN, result, null, reasonCode);
    }

    public Action action() {
        return action;
    }

    public AccountType intendedType() {
        return intendedType;
    }

    public AuthenticationResult result() {
        return result;
    }

    public Optional<PremiumAccount> premiumAccount() {
        return Optional.ofNullable(premiumAccount);
    }

    public Optional<UUID> expectedMojangUuid() {
        return premiumAccount().map(PremiumAccount::mojangUuid);
    }

    public String reasonCode() {
        return reasonCode;
    }
}
