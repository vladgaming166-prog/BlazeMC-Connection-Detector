package net.blazemc.connectiondetector.auth;

import java.util.Optional;
import java.util.UUID;
import net.blazemc.connectiondetector.config.PluginConfig;
import net.blazemc.connectiondetector.model.AccountType;
import net.blazemc.connectiondetector.model.AuthenticationResult;
import net.blazemc.connectiondetector.model.PremiumAccount;
import net.blazemc.connectiondetector.model.UuidSource;

/**
 * Velocity-independent authentication policy.
 *
 * <p>Premium identity is determined only by (1) an administrator reservation and
 * (2) a completed Mojang/Microsoft session whose UUID matches the stored UUID.
 * Client-claimed UUIDs and username existence are never enough.</p>
 */
public final class AuthenticationService {

    private final PremiumAccountStore premiumStore;
    private final PluginConfig config;
    private final ConcurrentLoginGuard concurrentLoginGuard;

    public AuthenticationService(
            PremiumAccountStore premiumStore,
            PluginConfig config,
            ConcurrentLoginGuard concurrentLoginGuard
    ) {
        this.premiumStore = premiumStore;
        this.config = config;
        this.concurrentLoginGuard = concurrentLoginGuard;
    }

    public PreLoginDecision decidePreLogin(String username, String connectionId) {
        if (!UsernameValidator.isValid(username)) {
            return PreLoginDecision.deny(AuthenticationResult.INVALID_USERNAME, "invalid_username");
        }

        Optional<PremiumAccount> reserved = findReserved(username);
        if (reserved.isPresent() && config.authentication().premium().enabled()) {
            if (!concurrentLoginGuard.tryAcquire(username, connectionId)) {
                return PreLoginDecision.deny(AuthenticationResult.CONCURRENT_LOGIN_DENIED, "concurrent_login");
            }
            if (config.authentication().premium().forceOnlineMode()) {
                return PreLoginDecision.forceOnline(reserved.get());
            }
        }

        concurrentLoginGuard.release(username, connectionId);
        if (config.authentication().cracked().preserveOfflineMode()) {
            return PreLoginDecision.forceOffline();
        }
        return PreLoginDecision.forceOffline();
    }

    public ProfileProtectionDecision protectProfile(ProfileSnapshot snapshot) {
        Optional<PremiumAccount> reserved = findReserved(snapshot.username());
        if (reserved.isEmpty() || !config.authentication().premium().enabled()) {
            return ProfileProtectionDecision.leaveUntouched();
        }
        if (snapshot.onlineMode()) {
            UUID sessionUuid = snapshot.originalUuid();
            if (sessionUuid == null) {
                return ProfileProtectionDecision.leaveUntouched();
            }
            return ProfileProtectionDecision.keepPremiumUuid(sessionUuid);
        }
        return ProfileProtectionDecision.leaveUntouched();
    }

    public LoginDecision verifyLogin(LoginSnapshot snapshot) {
        if (!UsernameValidator.isValid(snapshot.username())) {
            return LoginDecision.deny(
                    AuthenticationResult.INVALID_USERNAME,
                    AccountType.UNKNOWN,
                    "invalid_username",
                    snapshot.uuid(),
                    UuidSource.UNKNOWN
            );
        }

        Optional<PremiumAccount> reserved = findReserved(snapshot.username());
        if (reserved.isPresent() && config.authentication().premium().enabled()) {
            return verifyPremiumLogin(snapshot, reserved.get());
        }

        return LoginDecision.allowCracked(snapshot.uuid());
    }

    private LoginDecision verifyPremiumLogin(LoginSnapshot snapshot, PremiumAccount account) {
        boolean sessionOk = snapshot.onlineMode() && snapshot.mojangSessionEvidence();
        if (config.authentication().premium().requireMojangSession() && !sessionOk) {
            return LoginDecision.deny(
                    AuthenticationResult.PREMIUM_SESSION_MISSING,
                    AccountType.PREMIUM,
                    "premium_session_missing",
                    snapshot.uuid(),
                    snapshot.onlineMode() ? UuidSource.UNKNOWN : UuidSource.OFFLINE
            );
        }

        if (snapshot.uuid() != null && UuidNormalizer.isOfflineStyleUuid(snapshot.uuid(), snapshot.username())) {
            return LoginDecision.deny(
                    AuthenticationResult.PREMIUM_OFFLINE_UUID_REJECTED,
                    AccountType.PREMIUM,
                    "premium_offline_uuid",
                    snapshot.uuid(),
                    UuidSource.OFFLINE
            );
        }

        if (config.authentication().premium().verifyRegisteredUuid()
                && !UuidNormalizer.equals(account.mojangUuid(), snapshot.uuid())) {
            return LoginDecision.deny(
                    AuthenticationResult.PREMIUM_UUID_MISMATCH,
                    AccountType.PREMIUM,
                    "premium_uuid_mismatch",
                    snapshot.uuid(),
                    UuidSource.MOJANG_SESSION
            );
        }

        return LoginDecision.allowPremium(account, snapshot.uuid());
    }

    public Optional<PremiumAccount> findReserved(String username) {
        return premiumStore.findByUsername(username);
    }

    public void releaseConnection(String username, String connectionId) {
        concurrentLoginGuard.release(username, connectionId);
    }
}
