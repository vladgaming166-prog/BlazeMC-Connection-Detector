package net.blazemc.connectiondetector.limboauth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.blazemc.connectiondetector.auth.AuthenticationService;
import net.blazemc.connectiondetector.auth.ConcurrentLoginGuard;
import net.blazemc.connectiondetector.auth.InMemoryPremiumAccountStore;
import net.blazemc.connectiondetector.auth.LoginDecision;
import net.blazemc.connectiondetector.auth.LoginSnapshot;
import net.blazemc.connectiondetector.auth.UuidNormalizer;
import net.blazemc.connectiondetector.auth.VerifiedSession;
import net.blazemc.connectiondetector.config.PluginConfig;
import net.blazemc.connectiondetector.model.AccountType;
import net.blazemc.connectiondetector.model.PremiumAccount;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LimboAuthCompatibilityTest {

    @Test
    void verifiedPremiumSessionsBypassLimboAuthPasswordFlow() {
        assertTrue(LimboAuthCompatibilityAdapter.shouldBypassLimboAuth(true));
        assertFalse(LimboAuthCompatibilityAdapter.shouldBypassLimboAuth(false));
    }

    @Test
    void crackedPlayersDoNotBypassLimboAuth() {
        AuthenticationService service = new AuthenticationService(
                new InMemoryPremiumAccountStore(),
                PluginConfig.defaults(),
                new ConcurrentLoginGuard()
        );
        UUID offline = UuidNormalizer.offlineUuid("ExamplePlayer");
        LoginDecision login = service.verifyLogin(new LoginSnapshot("ExamplePlayer", offline, false, false, offline));
        VerifiedSession session = VerifiedSession.from(
                new LoginSnapshot("ExamplePlayer", offline, false, false, offline),
                login
        );
        assertEqualsCracked(session);
        assertFalse(LimboAuthCompatibilityAdapter.shouldBypassLimboAuth(session.verifiedPremium()));
    }

    @Test
    void reservedPremiumWithValidSessionIsMarkedForLimboAuthBypass() {
        InMemoryPremiumAccountStore store = new InMemoryPremiumAccountStore();
        UUID premiumUuid = UUID.fromString("11111111-1111-4111-8111-111111111111");
        store.insert(new PremiumAccount("Blaz3Craft", premiumUuid, Instant.now(), null, "Blaz3Craft", Instant.now()));
        AuthenticationService service = new AuthenticationService(
                store,
                PluginConfig.defaults(),
                new ConcurrentLoginGuard()
        );
        LoginDecision login = service.verifyLogin(new LoginSnapshot("Blaz3Craft", premiumUuid, true, true, null));
        VerifiedSession session = VerifiedSession.from(
                new LoginSnapshot("Blaz3Craft", premiumUuid, true, true, null),
                login
        );
        assertTrue(session.verifiedPremium());
        assertTrue(LimboAuthCompatibilityAdapter.shouldBypassLimboAuth(session.verifiedPremium()));
    }

    private static void assertEqualsCracked(VerifiedSession session) {
        assertTrue(session.accountType() == AccountType.CRACKED);
        assertFalse(session.verifiedPremium());
    }
}
