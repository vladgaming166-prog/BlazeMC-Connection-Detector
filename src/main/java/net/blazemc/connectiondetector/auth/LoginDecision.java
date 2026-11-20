package net.blazemc.connectiondetector.auth;

import java.util.UUID;
import net.blazemc.connectiondetector.model.AccountType;
import net.blazemc.connectiondetector.model.AuthenticationResult;
import net.blazemc.connectiondetector.model.PremiumAccount;
import net.blazemc.connectiondetector.model.UuidSource;

public final class LoginDecision {

    public enum Action {
        ALLOW,
        DENY
    }

    private final Action action;
    private final AccountType accountType;
    private final AuthenticationResult result;
    private final UUID forwardingUuid;
    private final UuidSource uuidSource;
    private final PremiumAccount premiumAccount;
    private final String reasonCode;
    private final boolean rewriteUuid;

    private LoginDecision(
            Action action,
            AccountType accountType,
            AuthenticationResult result,
            UUID forwardingUuid,
            UuidSource uuidSource,
            PremiumAccount premiumAccount,
            String reasonCode,
            boolean rewriteUuid
    ) {
        this.action = action;
        this.accountType = accountType;
        this.result = result;
        this.forwardingUuid = forwardingUuid;
        this.uuidSource = uuidSource;
        this.premiumAccount = premiumAccount;
        this.reasonCode = reasonCode;
        this.rewriteUuid = rewriteUuid;
    }

    public static LoginDecision allowPremium(PremiumAccount account, UUID forwardingUuid) {
        return new LoginDecision(
                Action.ALLOW,
                AccountType.PREMIUM,
                AuthenticationResult.PREMIUM_VERIFIED,
                forwardingUuid,
                UuidSource.MOJANG_SESSION,
                account,
                "premium_verified",
                false
        );
    }

    public static LoginDecision allowCracked(UUID forwardingUuid) {
        return new LoginDecision(
                Action.ALLOW,
                AccountType.CRACKED,
                AuthenticationResult.CRACKED_ALLOWED,
                forwardingUuid,
                UuidSource.OFFLINE,
                null,
                "cracked_allowed",
                false
        );
    }

    public static LoginDecision deny(
            AuthenticationResult result,
            AccountType accountType,
            String reasonCode,
            UUID uuid,
            UuidSource uuidSource
    ) {
        return new LoginDecision(Action.DENY, accountType, result, uuid, uuidSource, null, reasonCode, false);
    }

    public Action action() {
        return action;
    }

    public AccountType accountType() {
        return accountType;
    }

    public AuthenticationResult result() {
        return result;
    }

    public UUID forwardingUuid() {
        return forwardingUuid;
    }

    public UuidSource uuidSource() {
        return uuidSource;
    }

    public PremiumAccount premiumAccount() {
        return premiumAccount;
    }

    public String reasonCode() {
        return reasonCode;
    }

    public boolean rewriteUuid() {
        return rewriteUuid;
    }

    public boolean allowed() {
        return action == Action.ALLOW;
    }
}
