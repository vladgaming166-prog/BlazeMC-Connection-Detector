package net.blazemc.connectiondetector.limboauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.blazemc.connectiondetector.auth.AuthenticationService;
import net.blazemc.connectiondetector.auth.ConcurrentLoginGuard;
import net.blazemc.connectiondetector.auth.InMemoryPremiumAccountStore;
import net.blazemc.connectiondetector.auth.LoginDecision;
import net.blazemc.connectiondetector.auth.LoginSnapshot;
import net.blazemc.connectiondetector.auth.MojangLookupResult;
import net.blazemc.connectiondetector.auth.MojangProfile;
import net.blazemc.connectiondetector.auth.PendingAuthSession;
import net.blazemc.connectiondetector.auth.PreLoginDecision;
import net.blazemc.connectiondetector.auth.UuidNormalizer;
import net.blazemc.connectiondetector.auth.VerifiedSession;
import net.blazemc.connectiondetector.config.PluginConfig;
import net.blazemc.connectiondetector.model.AccountType;
import net.blazemc.connectiondetector.model.UuidSource;
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
        PreLoginDecision pre = service.decideFromLookup(
                "ExamplePlayer", "c", MojangLookupResult.notFound());
        LoginSnapshot snapshot = new LoginSnapshot("ExamplePlayer", offline, false, false, offline);
        LoginDecision login = service.verifyLogin(
                snapshot, PendingAuthSession.from("c", "ExamplePlayer", pre, offline));
        VerifiedSession session = VerifiedSession.from(snapshot, login);
        assertEquals(AccountType.CRACKED, session.accountType());
        assertFalse(session.verifiedPremium());
        assertFalse(LimboAuthCompatibilityAdapter.shouldBypassLimboAuth(session.verifiedPremium()));
    }

    @Test
    void verifiedPremiumDoesNotReplaceAuthenticatedUuidForLimboAuth() {
        AuthenticationService service = new AuthenticationService(
                new InMemoryPremiumAccountStore(),
                PluginConfig.defaults(),
                new ConcurrentLoginGuard()
        );
        UUID premiumUuid = UUID.fromString("11111111-1111-4111-8111-111111111111");
        PreLoginDecision pre = service.decideFromLookup(
                "Blaz3Craft",
                "p",
                MojangLookupResult.found(new MojangProfile("Blaz3Craft", premiumUuid))
        );
        LoginSnapshot snapshot = new LoginSnapshot("Blaz3Craft", premiumUuid, true, true, null);
        LoginDecision login = service.verifyLogin(
                snapshot, PendingAuthSession.from("p", "Blaz3Craft", pre, null));
        VerifiedSession session = VerifiedSession.from(snapshot, login, PendingAuthSession.from(
                "p", "Blaz3Craft", pre, null));
        assertTrue(session.verifiedPremium());
        assertEquals(premiumUuid, session.uuid());
        assertEquals(UuidSource.MOJANG, session.uuidSource());
        assertFalse(UuidNormalizer.isOfflineStyleUuid(session.uuid(), "Blaz3Craft"));
        assertTrue(LimboAuthCompatibilityAdapter.shouldBypassLimboAuth(session.verifiedPremium()));
    }
}
