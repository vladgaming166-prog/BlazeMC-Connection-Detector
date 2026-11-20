package net.blazemc.connectiondetector.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import net.blazemc.connectiondetector.config.PluginConfig;
import net.blazemc.connectiondetector.model.AccountType;
import net.blazemc.connectiondetector.model.AuthenticationResult;
import net.blazemc.connectiondetector.model.PremiumAccount;
import net.blazemc.connectiondetector.model.UuidSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthenticationServiceTest {

    private static final String PREMIUM_NAME = "Blaz3Craft";
    private static final UUID PREMIUM_UUID = UUID.fromString("11111111-1111-4111-8111-111111111111");

    private InMemoryPremiumAccountStore store;
    private AuthenticationService service;

    @BeforeEach
    void setUp() {
        store = new InMemoryPremiumAccountStore();
        store.insert(new PremiumAccount(PREMIUM_NAME, PREMIUM_UUID, Instant.now(), null, PREMIUM_NAME, Instant.now()));
        service = new AuthenticationService(store, PluginConfig.defaults(), new ConcurrentLoginGuard());
    }

    @Test
    void legitimateRegisteredPremiumAccountForcesOnlineModeAndAcceptsMatchingSession() {
        PreLoginDecision preLogin = service.decidePreLogin(PREMIUM_NAME, "conn-1");
        assertEquals(PreLoginDecision.Action.FORCE_ONLINE, preLogin.action());
        assertEquals(AccountType.PREMIUM, preLogin.intendedType());

        LoginDecision login = service.verifyLogin(new LoginSnapshot(
                PREMIUM_NAME,
                PREMIUM_UUID,
                true,
                true,
                UuidNormalizer.offlineUuid(PREMIUM_NAME)
        ));
        assertTrue(login.allowed());
        assertEquals(AccountType.PREMIUM, login.accountType());
        assertEquals(AuthenticationResult.PREMIUM_VERIFIED, login.result());
        assertEquals(PREMIUM_UUID, login.forwardingUuid());
        assertEquals(UuidSource.MOJANG_SESSION, login.uuidSource());
        assertNotEquals(UuidNormalizer.offlineUuid(PREMIUM_NAME), login.forwardingUuid());
    }

    @Test
    void crackedPlayerWithUnreservedUsernameStaysOffline() {
        PreLoginDecision preLogin = service.decidePreLogin("ExamplePlayer", "conn-2");
        assertEquals(PreLoginDecision.Action.FORCE_OFFLINE, preLogin.action());
        assertEquals(AccountType.CRACKED, preLogin.intendedType());

        UUID offline = UuidNormalizer.offlineUuid("ExamplePlayer");
        LoginDecision login = service.verifyLogin(new LoginSnapshot("ExamplePlayer", offline, false, false, offline));
        assertTrue(login.allowed());
        assertEquals(AccountType.CRACKED, login.accountType());
        assertEquals(offline, login.forwardingUuid());
        assertEquals(UuidSource.OFFLINE, login.uuidSource());
    }

    @Test
    void unauthorizedClientUsingReservedPremiumUsernameIsRejected() {
        PreLoginDecision preLogin = service.decidePreLogin(PREMIUM_NAME, "attacker");
        assertEquals(PreLoginDecision.Action.FORCE_ONLINE, preLogin.action());

        UUID offline = UuidNormalizer.offlineUuid(PREMIUM_NAME);
        LoginDecision login = service.verifyLogin(new LoginSnapshot(
                PREMIUM_NAME,
                offline,
                false,
                false,
                offline
        ));
        assertFalse(login.allowed());
        assertEquals(AuthenticationResult.PREMIUM_SESSION_MISSING, login.result());
        assertEquals(AccountType.PREMIUM, login.accountType());
    }

    @Test
    void premiumPlayerConnectingWithDifferentOfflineUuidIsRejectedAndNeverRewritten() {
        UUID offline = UuidNormalizer.offlineUuid(PREMIUM_NAME);
        assertNotEquals(PREMIUM_UUID, offline);

        LoginDecision offlineAttempt = service.verifyLogin(new LoginSnapshot(
                PREMIUM_NAME,
                offline,
                false,
                false,
                offline
        ));
        assertFalse(offlineAttempt.allowed());
        assertEquals(AuthenticationResult.PREMIUM_SESSION_MISSING, offlineAttempt.result());

        ProfileProtectionDecision protection = service.protectProfile(
                new ProfileSnapshot(PREMIUM_NAME, PREMIUM_UUID, offline, true)
        );
        assertEquals(PREMIUM_UUID, protection.uuidToKeep().orElseThrow());
        assertTrue(protection.restoreOriginal());
    }

    @Test
    void incorrectPremiumUuidIsRejected() {
        UUID otherPremium = UUID.fromString("22222222-2222-4222-8222-222222222222");
        LoginDecision login = service.verifyLogin(new LoginSnapshot(
                PREMIUM_NAME,
                otherPremium,
                true,
                true,
                otherPremium
        ));
        assertFalse(login.allowed());
        assertEquals(AuthenticationResult.PREMIUM_UUID_MISMATCH, login.result());
    }

    @Test
    void clientClaimedUuidIsNeverUsedToClassifyPremium() {
        PreLoginDecision cracked = service.decidePreLogin("NotchFan", "conn-claimed");
        assertEquals(PreLoginDecision.Action.FORCE_OFFLINE, cracked.action());

        LoginDecision login = service.verifyLogin(new LoginSnapshot(
                "NotchFan",
                UuidNormalizer.offlineUuid("NotchFan"),
                false,
                false,
                PREMIUM_UUID
        ));
        assertEquals(AccountType.CRACKED, login.accountType());
        assertTrue(login.allowed());
    }

    @Test
    void unreservedUsernameThatLooksPremiumStaysCracked() {
        LoginDecision login = service.verifyLogin(new LoginSnapshot(
                "Notch",
                UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5"),
                true,
                true,
                null
        ));
        assertEquals(AccountType.CRACKED, login.accountType());
        assertTrue(login.allowed());
    }

    @Test
    void concurrentReservedPremiumConnectionsAreDenied() {
        PreLoginDecision first = service.decidePreLogin(PREMIUM_NAME, "first");
        PreLoginDecision second = service.decidePreLogin(PREMIUM_NAME, "second");
        assertEquals(PreLoginDecision.Action.FORCE_ONLINE, first.action());
        assertEquals(PreLoginDecision.Action.DENY, second.action());
        assertEquals(AuthenticationResult.CONCURRENT_LOGIN_DENIED, second.result());
    }

    @Test
    void invalidUsernameIsDenied() {
        PreLoginDecision decision = service.decidePreLogin("ab", "conn");
        assertEquals(PreLoginDecision.Action.DENY, decision.action());
        assertEquals(AuthenticationResult.INVALID_USERNAME, decision.result());
    }
}
