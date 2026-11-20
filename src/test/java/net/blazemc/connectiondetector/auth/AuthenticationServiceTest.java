package net.blazemc.connectiondetector.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.blazemc.connectiondetector.config.PluginConfig;
import net.blazemc.connectiondetector.model.AccountType;
import net.blazemc.connectiondetector.model.AuthenticationResult;
import net.blazemc.connectiondetector.model.ConnectionState;
import net.blazemc.connectiondetector.model.PremiumAccount;
import net.blazemc.connectiondetector.model.UuidSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthenticationServiceTest {

    private static final String PREMIUM_NAME = "Blaz3Craft";
    private static final UUID PREMIUM_UUID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final MojangProfile PREMIUM_PROFILE = new MojangProfile(PREMIUM_NAME, PREMIUM_UUID);

    private InMemoryPremiumAccountStore store;
    private AuthenticationService service;

    @BeforeEach
    void setUp() {
        store = new InMemoryPremiumAccountStore();
        service = new AuthenticationService(store, PluginConfig.defaults(), new ConcurrentLoginGuard());
    }

    @Test
    void genuinePremiumProfileIsDetectedAutomaticallyWithoutManualRegistration() {
        assertTrue(store.findByUsername(PREMIUM_NAME).isEmpty());
        PreLoginDecision preLogin = service.decideFromLookup(
                PREMIUM_NAME, "conn-auto", MojangLookupResult.found(PREMIUM_PROFILE));
        assertEquals(PreLoginDecision.Action.FORCE_ONLINE, preLogin.action());
        assertEquals(ConnectionState.PREMIUM_CANDIDATE, preLogin.connectionState());
        assertTrue(preLogin.autoDetected());
        assertEquals(PREMIUM_UUID, preLogin.expectedMojangUuid().orElseThrow());
    }

    @Test
    void newlyJoiningPremiumPlayerReceivesTheRealMojangUuid() {
        PreLoginDecision preLogin = service.decideFromLookup(
                PREMIUM_NAME, "conn-new", MojangLookupResult.found(PREMIUM_PROFILE));
        LoginDecision login = verify(preLogin, new LoginSnapshot(
                PREMIUM_NAME, PREMIUM_UUID, true, true, null), "conn-new");
        assertTrue(login.allowed());
        assertEquals(AuthenticationResult.PREMIUM_VERIFIED, login.result());
        assertEquals(PREMIUM_UUID, login.forwardingUuid());
        assertEquals(UuidSource.MOJANG, login.uuidSource());
        assertNotEquals(UuidNormalizer.offlineUuid(PREMIUM_NAME), login.forwardingUuid());
    }

    @Test
    void knownPremiumUsernameForcesOnlineAuthenticationWithoutLookup() {
        store.insert(new PremiumAccount(PREMIUM_NAME, PREMIUM_UUID, Instant.now(), null, PREMIUM_NAME, Instant.now()));
        Optional<PreLoginDecision> immediate = service.tryImmediateDecision(PREMIUM_NAME, "cached");
        assertTrue(immediate.isPresent());
        assertEquals(PreLoginDecision.Action.FORCE_ONLINE, immediate.get().action());
        assertEquals(PREMIUM_UUID, immediate.get().expectedMojangUuid().orElseThrow());
    }

    @Test
    void crackedLauncherUsingAPremiumUsernameIsRejected() {
        PreLoginDecision preLogin = service.decideFromLookup(
                PREMIUM_NAME, "attacker", MojangLookupResult.found(PREMIUM_PROFILE));
        UUID offline = UuidNormalizer.offlineUuid(PREMIUM_NAME);
        LoginDecision login = verify(preLogin, new LoginSnapshot(
                PREMIUM_NAME, offline, false, false, offline), "attacker");
        assertFalse(login.allowed());
        assertEquals(AuthenticationResult.PREMIUM_SESSION_MISSING, login.result());
        assertEquals(AccountType.PREMIUM, login.accountType());
        assertNotEquals(AuthenticationResult.CRACKED_CONFIRMED, login.result());
    }

    @Test
    void premiumSessionWithInvalidUuidIsRejected() {
        PreLoginDecision preLogin = service.decideFromLookup(
                PREMIUM_NAME, "bad-uuid", MojangLookupResult.found(PREMIUM_PROFILE));
        UUID otherPremium = UUID.fromString("22222222-2222-4222-8222-222222222222");
        LoginDecision login = verify(preLogin, new LoginSnapshot(
                PREMIUM_NAME, otherPremium, true, true, otherPremium), "bad-uuid");
        assertFalse(login.allowed());
        assertEquals(AuthenticationResult.PREMIUM_UUID_MISMATCH, login.result());
    }

    @Test
    void failedPremiumSessionNeverFallsBackToOfflineMode() {
        store.insert(new PremiumAccount(PREMIUM_NAME, PREMIUM_UUID, Instant.now(), null, PREMIUM_NAME, Instant.now()));
        LoginDecision login = service.verifyLogin(new LoginSnapshot(
                PREMIUM_NAME,
                UuidNormalizer.offlineUuid(PREMIUM_NAME),
                false,
                false,
                UuidNormalizer.offlineUuid(PREMIUM_NAME)
        ));
        assertFalse(login.allowed());
        assertEquals(AccountType.PREMIUM, login.accountType());
        assertNotEquals(AuthenticationResult.CRACKED_CONFIRMED, login.result());
        assertNotEquals(AuthenticationResult.CRACKED_ALLOWED, login.result());
    }

    @Test
    void genuineCrackedPlayerWithDefinitiveProfileNotFoundIsAllowed() {
        PreLoginDecision preLogin = service.decideFromLookup(
                "ExamplePlayer", "cracked", MojangLookupResult.notFound());
        assertEquals(PreLoginDecision.Action.FORCE_OFFLINE, preLogin.action());
        assertEquals(ConnectionState.CRACKED_CONFIRMED, preLogin.connectionState());

        UUID offline = UuidNormalizer.offlineUuid("ExamplePlayer");
        LoginDecision login = verify(preLogin, new LoginSnapshot(
                "ExamplePlayer", offline, false, false, offline), "cracked");
        assertTrue(login.allowed());
        assertEquals(AccountType.CRACKED, login.accountType());
        assertEquals(offline, login.forwardingUuid());
        assertEquals(UuidSource.OFFLINE, login.uuidSource());
    }

    @Test
    void mojangHttp429DoesNotAllowCrackedFallback() {
        PreLoginDecision decision = service.decideFromLookup(
                "MaybePremium", "r429", MojangLookupResult.rateLimited(429));
        assertEquals(PreLoginDecision.Action.DENY, decision.action());
        assertEquals(AuthenticationResult.MOJANG_API_UNAVAILABLE, decision.result());
        assertEquals(ConnectionState.MOJANG_API_UNAVAILABLE, decision.connectionState());
    }

    @Test
    void mojangHttp5xxDoesNotAllowCrackedFallback() {
        PreLoginDecision decision = service.decideFromLookup(
                "MaybePremium", "r500", MojangLookupResult.error("HTTP 503", 503));
        assertEquals(PreLoginDecision.Action.DENY, decision.action());
        assertEquals(AuthenticationResult.MOJANG_API_UNAVAILABLE, decision.result());
    }

    @Test
    void mojangTimeoutDoesNotAllowCrackedFallback() {
        PreLoginDecision decision = service.decideFromLookup(
                "MaybePremium", "timeout", MojangLookupResult.timeout("timed out"));
        assertEquals(PreLoginDecision.Action.DENY, decision.action());
        assertEquals(AuthenticationResult.MOJANG_API_UNAVAILABLE, decision.result());
    }

    @Test
    void unknownProfileFollowsStrictFailClosedPolicy() {
        PreLoginDecision missingLookup = service.tryImmediateDecision("UnknownUser", "unknown")
                .orElse(PreLoginDecision.deny(
                        AuthenticationResult.MOJANG_API_UNAVAILABLE,
                        ConnectionState.MOJANG_API_UNAVAILABLE,
                        "pending_lookup"));
        assertTrue(service.tryImmediateDecision("UnknownUser", "unknown").isEmpty());

        LoginDecision login = service.verifyLogin(new LoginSnapshot(
                "UnknownUser",
                UuidNormalizer.offlineUuid("UnknownUser"),
                false,
                false,
                null
        ));
        assertFalse(login.allowed());
        assertEquals(AuthenticationResult.MOJANG_API_UNAVAILABLE, login.result());
        assertEquals(missingLookup.connectionState(), ConnectionState.MOJANG_API_UNAVAILABLE);
    }

    @Test
    void usernameChangePreservesOfficialUuidIdentity() {
        store.insert(new PremiumAccount(PREMIUM_NAME, PREMIUM_UUID, Instant.now(), null, PREMIUM_NAME, Instant.now()));
        MojangProfile renamed = new MojangProfile("BlazeCraft", PREMIUM_UUID);
        PreLoginDecision preLogin = service.decideFromLookup(
                "BlazeCraft", "rename", MojangLookupResult.found(renamed));
        assertEquals(PreLoginDecision.Action.FORCE_ONLINE, preLogin.action());
        LoginDecision login = verify(preLogin, new LoginSnapshot(
                "BlazeCraft", PREMIUM_UUID, true, true, null), "rename");
        assertTrue(login.allowed());
        assertEquals(PREMIUM_UUID, login.forwardingUuid());
        assertEquals(UuidSource.MOJANG, login.uuidSource());
    }

    @Test
    void premiumUuidReachesBackendUnchanged() {
        PreLoginDecision preLogin = service.decideFromLookup(
                PREMIUM_NAME, "fwd", MojangLookupResult.found(PREMIUM_PROFILE));
        LoginSnapshot snapshot = new LoginSnapshot(PREMIUM_NAME, PREMIUM_UUID, true, true, null);
        LoginDecision login = verify(preLogin, snapshot, "fwd");
        VerifiedSession session = VerifiedSession.from(
                snapshot, login, PendingAuthSession.from("fwd", PREMIUM_NAME, preLogin, null));
        assertEquals(PREMIUM_UUID, session.uuid());
        assertEquals(PREMIUM_UUID, session.backendForwardingUuid());
        assertEquals(session.uuid(), session.backendForwardingUuid());
        assertFalse(UuidNormalizer.isOfflineStyleUuid(session.backendForwardingUuid(), PREMIUM_NAME));
    }

    @Test
    void existingCrackedUuidsRemainUnchanged() {
        UUID offline = UuidNormalizer.offlineUuid("ExamplePlayer");
        PreLoginDecision preLogin = service.decideFromLookup(
                "ExamplePlayer", "keep", MojangLookupResult.notFound());
        LoginDecision login = verify(preLogin, new LoginSnapshot(
                "ExamplePlayer", offline, false, false, offline), "keep");
        assertEquals(offline, login.forwardingUuid());
        assertFalse(login.rewriteUuid());
    }

    @Test
    void premiumAndCrackedProfilesNeverMergeByUsername() {
        UUID cracked = UuidNormalizer.offlineUuid(PREMIUM_NAME);
        assertNotEquals(PREMIUM_UUID, cracked);
        PreLoginDecision premium = service.decideFromLookup(
                PREMIUM_NAME, "prem", MojangLookupResult.found(PREMIUM_PROFILE));
        LoginDecision premiumLogin = verify(premium, new LoginSnapshot(
                PREMIUM_NAME, PREMIUM_UUID, true, true, cracked), "prem");
        assertEquals(PREMIUM_UUID, premiumLogin.forwardingUuid());
        assertNotEquals(cracked, premiumLogin.forwardingUuid());
    }

    @Test
    void concurrentPremiumConnectionsAreDeniedSafely() {
        PreLoginDecision first = service.decideFromLookup(
                PREMIUM_NAME, "first", MojangLookupResult.found(PREMIUM_PROFILE));
        PreLoginDecision second = service.decideFromLookup(
                PREMIUM_NAME, "second", MojangLookupResult.found(PREMIUM_PROFILE));
        assertEquals(PreLoginDecision.Action.FORCE_ONLINE, first.action());
        assertEquals(PreLoginDecision.Action.DENY, second.action());
        assertEquals(AuthenticationResult.CONCURRENT_LOGIN_DENIED, second.result());
    }

    @Test
    void cachedPremiumProfilesStayProtectedDuringApiOutage() {
        store.insert(new PremiumAccount(PREMIUM_NAME, PREMIUM_UUID, Instant.now(), null, PREMIUM_NAME, Instant.now()));
        Optional<PreLoginDecision> immediate = service.tryImmediateDecision(PREMIUM_NAME, "outage");
        assertEquals(PreLoginDecision.Action.FORCE_ONLINE, immediate.orElseThrow().action());
        PreLoginDecision fromError = service.decideFromLookup(
                PREMIUM_NAME, "outage", MojangLookupResult.error("HTTP 503", 503));
        assertEquals(PreLoginDecision.Action.FORCE_ONLINE, fromError.action());
        assertEquals(PREMIUM_UUID, fromError.expectedMojangUuid().orElseThrow());
    }

    @Test
    void verifiedPremiumSessionIsNeverAnOfflineUuid() {
        PreLoginDecision preLogin = service.decideFromLookup(
                PREMIUM_NAME, "no-offline", MojangLookupResult.found(PREMIUM_PROFILE));
        LoginDecision login = verify(preLogin, new LoginSnapshot(
                PREMIUM_NAME, PREMIUM_UUID, true, true, null), "no-offline");
        VerifiedSession session = VerifiedSession.from(
                new LoginSnapshot(PREMIUM_NAME, PREMIUM_UUID, true, true, null),
                login,
                PendingAuthSession.from("no-offline", PREMIUM_NAME, preLogin, null)
        );
        assertTrue(session.verifiedPremium());
        assertEquals(UuidSource.MOJANG, session.uuidSource());
        assertFalse(UuidNormalizer.isOfflineStyleUuid(session.uuid(), PREMIUM_NAME));
    }

    @Test
    void clientClaimedUuidIsNeverUsedToClassifyPremium() {
        PreLoginDecision cracked = service.decideFromLookup(
                "NotchFan", "claimed", MojangLookupResult.notFound());
        assertEquals(PreLoginDecision.Action.FORCE_OFFLINE, cracked.action());
        LoginDecision login = verify(cracked, new LoginSnapshot(
                "NotchFan",
                UuidNormalizer.offlineUuid("NotchFan"),
                false,
                false,
                PREMIUM_UUID
        ), "claimed");
        assertEquals(AccountType.CRACKED, login.accountType());
        assertTrue(login.allowed());
        assertEquals(UuidNormalizer.offlineUuid("NotchFan"), login.forwardingUuid());
    }

    @Test
    void invalidUsernameIsDenied() {
        Optional<PreLoginDecision> decision = service.tryImmediateDecision("ab", "conn");
        assertEquals(PreLoginDecision.Action.DENY, decision.orElseThrow().action());
        assertEquals(AuthenticationResult.INVALID_USERNAME, decision.get().result());
    }

    @Test
    void protectProfileKeepsAuthenticatedMojangUuid() {
        store.insert(new PremiumAccount(PREMIUM_NAME, PREMIUM_UUID, Instant.now(), null, PREMIUM_NAME, Instant.now()));
        UUID offline = UuidNormalizer.offlineUuid(PREMIUM_NAME);
        ProfileProtectionDecision protection = service.protectProfile(
                new ProfileSnapshot(PREMIUM_NAME, PREMIUM_UUID, offline, true)
        );
        assertEquals(PREMIUM_UUID, protection.uuidToKeep().orElseThrow());
        assertTrue(protection.restoreOriginal());
    }

    private LoginDecision verify(PreLoginDecision preLogin, LoginSnapshot snapshot, String connectionId) {
        return service.verifyLogin(
                snapshot,
                PendingAuthSession.from(connectionId, snapshot.username(), preLogin, snapshot.clientClaimedUuid())
        );
    }
}
