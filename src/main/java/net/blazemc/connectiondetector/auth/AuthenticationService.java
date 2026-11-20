package net.blazemc.connectiondetector.auth;

import java.util.Optional;
import java.util.UUID;
import net.blazemc.connectiondetector.config.PluginConfig;
import net.blazemc.connectiondetector.model.AccountType;
import net.blazemc.connectiondetector.model.AuthenticationResult;
import net.blazemc.connectiondetector.model.ConnectionState;
import net.blazemc.connectiondetector.model.PremiumAccount;
import net.blazemc.connectiondetector.model.UuidSource;

/**
 * Velocity-independent authentication policy.
 *
 * <p>A Mojang Java profile lookup can mark a connection as {@code PREMIUM_CANDIDATE}.
 * That is never enough. Ownership requires a completed Mojang/Microsoft session
 * whose UUID matches the expected Mojang UUID. HTTP errors are never treated as
 * proof that a name is cracked.</p>
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

    /**
     * Returns a decision that does not need a live Mojang call, or empty when
     * automatic detection must query the profile API.
     */
    public Optional<PreLoginDecision> tryImmediateDecision(String username, String connectionId) {
        if (!UsernameValidator.isValid(username)) {
            return Optional.of(PreLoginDecision.deny(
                    AuthenticationResult.INVALID_USERNAME,
                    ConnectionState.AUTHENTICATION_FAILED,
                    "invalid_username"
            ));
        }
        if (!config.premiumDetection().enabled()) {
            return Optional.of(PreLoginDecision.forceOffline());
        }

        Optional<PremiumAccount> reserved = findReserved(username);
        if (reserved.isPresent() && config.premiumDetection().protectPremiumUsernames()) {
            return Optional.of(lockAndForceOnline(username, connectionId, reserved.get()));
        }
        if (!config.premiumDetection().automatic()) {
            return Optional.of(PreLoginDecision.forceOffline());
        }
        return Optional.empty();
    }

    public PreLoginDecision decideFromLookup(String username, String connectionId, MojangLookupResult lookup) {
        Optional<PreLoginDecision> immediate = tryImmediateDecision(username, connectionId);
        if (immediate.isPresent()) {
            return immediate.get();
        }

        if (lookup.status() == MojangLookupResult.Status.FOUND && lookup.profile().isPresent()) {
            MojangProfile profile = lookup.profile().get();
            Optional<PremiumAccount> byUuid = premiumStore.findByUuid(profile.uuid());
            if (byUuid.isPresent()) {
                return lockAndForceOnline(username, connectionId, byUuid.get());
            }
            if (!config.premiumDetection().forceOnlineAuthentication()) {
                return PreLoginDecision.forceOffline();
            }
            if (!concurrentLoginGuard.tryAcquire(username, connectionId)) {
                return PreLoginDecision.deny(
                        AuthenticationResult.CONCURRENT_LOGIN_DENIED,
                        ConnectionState.AUTHENTICATION_FAILED,
                        "concurrent_login"
                );
            }
            return PreLoginDecision.forceOnlineCandidate(profile.uuid(), true);
        }

        if (lookup.status() == MojangLookupResult.Status.NOT_FOUND) {
            concurrentLoginGuard.release(username, connectionId);
            if (config.cracked().enabled() && config.cracked().requireDefinitiveProfileNotFound()) {
                return PreLoginDecision.forceOffline();
            }
            if (config.cracked().enabled()) {
                return PreLoginDecision.forceOffline();
            }
            return PreLoginDecision.deny(
                    AuthenticationResult.DENIED,
                    ConnectionState.AUTHENTICATION_FAILED,
                    "cracked_disabled"
            );
        }

        concurrentLoginGuard.release(username, connectionId);
        if (config.failClosed() && config.premiumDetection().requireRealProfile()) {
            return PreLoginDecision.deny(
                    AuthenticationResult.MOJANG_API_UNAVAILABLE,
                    ConnectionState.MOJANG_API_UNAVAILABLE,
                    "mojang_unavailable"
            );
        }
        // Availability mode only: unknown names may continue offline. Known
        // Premium usernames never reach this branch (tryImmediateDecision).
        return PreLoginDecision.forceOffline();
    }

    public ProfileProtectionDecision protectProfile(ProfileSnapshot snapshot) {
        return protectProfile(snapshot, Optional.empty());
    }

    public ProfileProtectionDecision protectProfile(ProfileSnapshot snapshot, Optional<PendingAuthSession> pending) {
        boolean premiumCandidate = pending.map(PendingAuthSession::premiumCandidate).orElse(false)
                || findReserved(snapshot.username()).isPresent();
        if (!premiumCandidate || !config.premiumDetection().enabled()) {
            return ProfileProtectionDecision.leaveUntouched();
        }
        if (snapshot.onlineMode() && snapshot.originalUuid() != null) {
            if (config.uuid().preventPremiumOfflineUuid()
                    && UuidNormalizer.isOfflineStyleUuid(snapshot.originalUuid(), snapshot.username())) {
                return ProfileProtectionDecision.leaveUntouched();
            }
            return ProfileProtectionDecision.keepPremiumUuid(snapshot.originalUuid());
        }
        return ProfileProtectionDecision.leaveUntouched();
    }

    public LoginDecision verifyLogin(LoginSnapshot snapshot) {
        return verifyLogin(snapshot, Optional.empty());
    }

    public LoginDecision verifyLogin(LoginSnapshot snapshot, PendingAuthSession pending) {
        return verifyLogin(snapshot, Optional.ofNullable(pending));
    }

    public LoginDecision verifyLogin(LoginSnapshot snapshot, Optional<PendingAuthSession> pending) {
        if (!UsernameValidator.isValid(snapshot.username())) {
            return LoginDecision.deny(
                    AuthenticationResult.INVALID_USERNAME,
                    AccountType.UNKNOWN,
                    "invalid_username",
                    snapshot.uuid(),
                    UuidSource.UNKNOWN
            );
        }

        boolean premiumCandidate = pending.map(PendingAuthSession::premiumCandidate).orElse(false)
                || findReserved(snapshot.username()).isPresent();
        if (premiumCandidate && config.premiumDetection().enabled()) {
            UUID expected = pending.map(PendingAuthSession::expectedMojangUuid)
                    .orElseGet(() -> findReserved(snapshot.username()).map(PremiumAccount::mojangUuid).orElse(null));
            return verifyPremiumLogin(snapshot, expected);
        }

        if (pending.map(session -> session.connectionState() == ConnectionState.CRACKED_CONFIRMED).orElse(false)) {
            return LoginDecision.allowCracked(snapshot.uuid());
        }

        if (config.failClosed() && config.premiumDetection().automatic()) {
            return LoginDecision.deny(
                    AuthenticationResult.MOJANG_API_UNAVAILABLE,
                    AccountType.UNKNOWN,
                    "unknown_without_lookup",
                    snapshot.uuid(),
                    UuidSource.UNKNOWN
            );
        }
        return LoginDecision.allowCracked(snapshot.uuid());
    }

    private LoginDecision verifyPremiumLogin(LoginSnapshot snapshot, UUID expectedUuid) {
        boolean sessionOk = snapshot.onlineMode() && snapshot.mojangSessionEvidence();
        if (config.premiumDetection().requireAuthenticatedSession() && !sessionOk) {
            if (config.premiumDetection().neverDowngradeToCracked()
                    || config.premiumDetection().protectPremiumUsernames()) {
                return LoginDecision.deny(
                        AuthenticationResult.PREMIUM_SESSION_MISSING,
                        AccountType.PREMIUM,
                        "premium_session_missing",
                        snapshot.uuid(),
                        snapshot.onlineMode() ? UuidSource.UNKNOWN : UuidSource.OFFLINE
                );
            }
            return LoginDecision.allowCracked(snapshot.uuid());
        }

        if (config.uuid().preventPremiumOfflineUuid()
                && snapshot.uuid() != null
                && UuidNormalizer.isOfflineStyleUuid(snapshot.uuid(), snapshot.username())) {
            return LoginDecision.deny(
                    AuthenticationResult.PREMIUM_OFFLINE_UUID_REJECTED,
                    AccountType.PREMIUM,
                    "premium_offline_uuid",
                    snapshot.uuid(),
                    UuidSource.OFFLINE
            );
        }

        if (config.premiumDetection().verifyMojangUuid()
                && expectedUuid != null
                && !UuidNormalizer.equals(expectedUuid, snapshot.uuid())) {
            return LoginDecision.deny(
                    AuthenticationResult.PREMIUM_UUID_MISMATCH,
                    AccountType.PREMIUM,
                    "premium_uuid_mismatch",
                    snapshot.uuid(),
                    UuidSource.MOJANG
            );
        }

        PremiumAccount stored = expectedUuid == null
                ? findReserved(snapshot.username()).orElse(null)
                : premiumStore.findByUuid(expectedUuid).orElse(findReserved(snapshot.username()).orElse(null));
        return LoginDecision.allowPremium(stored, snapshot.uuid());
    }

    private PreLoginDecision lockAndForceOnline(String username, String connectionId, PremiumAccount account) {
        if (!concurrentLoginGuard.tryAcquire(username, connectionId)) {
            return PreLoginDecision.deny(
                    AuthenticationResult.CONCURRENT_LOGIN_DENIED,
                    ConnectionState.AUTHENTICATION_FAILED,
                    "concurrent_login"
            );
        }
        return PreLoginDecision.forceOnline(account);
    }

    public Optional<PremiumAccount> findReserved(String username) {
        return premiumStore.findByUsername(username);
    }

    public void releaseConnection(String username, String connectionId) {
        concurrentLoginGuard.release(username, connectionId);
    }
}
