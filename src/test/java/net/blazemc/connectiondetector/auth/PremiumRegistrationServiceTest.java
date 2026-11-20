package net.blazemc.connectiondetector.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.blazemc.connectiondetector.config.PluginConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PremiumRegistrationServiceTest {

    private static final UUID BLAZE_UUID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID OTHER_UUID = UUID.fromString("33333333-3333-4333-8333-333333333333");

    private InMemoryPremiumAccountStore store;
    private FakeMojangApi api;
    private PremiumRegistrationService service;

    @BeforeEach
    void setUp() {
        store = new InMemoryPremiumAccountStore();
        api = new FakeMojangApi()
                .put("Blaz3Craft", FakeMojangApi.profile("Blaz3Craft", BLAZE_UUID.toString()))
                .put("BlazeCraft", FakeMojangApi.profile("BlazeCraft", BLAZE_UUID.toString()))
                .put("OtherUser", FakeMojangApi.profile("OtherUser", OTHER_UUID.toString()));
        service = new PremiumRegistrationService(store, api, PluginConfig.defaults());
    }

    @Test
    void duplicatePremiumRegistrationIsRejected() {
        assertEquals(PremiumRegistrationService.Outcome.REGISTERED, service.add("Blaz3Craft").outcome());
        PremiumRegistrationService.Result duplicate = service.add("blaz3craft");
        assertEquals(PremiumRegistrationService.Outcome.DUPLICATE_USERNAME, duplicate.outcome());
        assertEquals(1, store.listAll().size());
    }

    @Test
    void duplicateUuidRegistrationIsRejected() {
        assertEquals(PremiumRegistrationService.Outcome.REGISTERED, service.add("Blaz3Craft").outcome());
        PremiumRegistrationService.Result duplicate = service.add("BlazeCraft");
        assertEquals(PremiumRegistrationService.Outcome.DUPLICATE_UUID, duplicate.outcome());
    }

    @Test
    void usernameChangeKeepsTheSameMojangUuid() {
        service.add("Blaz3Craft");
        PremiumRegistrationService.Result renamed = service.rename("Blaz3Craft", "BlazeCraft");
        assertEquals(PremiumRegistrationService.Outcome.RENAMED, renamed.outcome());
        assertEquals("BlazeCraft", renamed.account().canonicalUsername());
        assertEquals(BLAZE_UUID, renamed.account().mojangUuid());
        assertTrue(store.findByUsername("BlazeCraft").isPresent());
        assertTrue(store.findByUsername("Blaz3Craft").isEmpty());
    }

    @Test
    void renameToDifferentUuidIsRejected() {
        service.add("Blaz3Craft");
        PremiumRegistrationService.Result result = service.rename("Blaz3Craft", "OtherUser");
        assertEquals(PremiumRegistrationService.Outcome.UUID_MISMATCH, result.outcome());
        assertEquals("Blaz3Craft", store.findByUuid(BLAZE_UUID).orElseThrow().canonicalUsername());
    }

    @Test
    void mojangApiOutageDoesNotRegisterAndDoesNotMarkCracked() {
        api.put("TempName", MojangLookupResult.error("connection reset", 503));
        PremiumRegistrationService.Result result = service.add("TempName");
        assertEquals(PremiumRegistrationService.Outcome.API_UNAVAILABLE, result.outcome());
        assertTrue(store.findByUsername("TempName").isEmpty());
        assertFalse(result.success());
    }

    @Test
    void cachedPremiumIdentityRemainsDuringApiOutage() {
        service.add("Blaz3Craft");
        api.put("Blaz3Craft", MojangLookupResult.timeout("timed out"));
        PremiumRegistrationService.Result refresh = service.refresh("Blaz3Craft");
        assertEquals(PremiumRegistrationService.Outcome.API_UNAVAILABLE, refresh.outcome());
        assertEquals(BLAZE_UUID, store.findByUsername("Blaz3Craft").orElseThrow().mojangUuid());
    }

    @Test
    void notFoundIsNotTreatedAsApiFailure() {
        PremiumRegistrationService.Result result = service.add("CrackedGuy");
        assertEquals(PremiumRegistrationService.Outcome.NOT_PREMIUM, result.outcome());
    }
}
