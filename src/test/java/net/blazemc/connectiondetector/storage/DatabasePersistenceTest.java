package net.blazemc.connectiondetector.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.blazemc.connectiondetector.auth.LoginDecision;
import net.blazemc.connectiondetector.auth.LoginSnapshot;
import net.blazemc.connectiondetector.auth.UuidNormalizer;
import net.blazemc.connectiondetector.auth.VerifiedSession;
import net.blazemc.connectiondetector.config.PluginConfig;
import net.blazemc.connectiondetector.model.AccountType;
import net.blazemc.connectiondetector.model.IdentityRecord;
import net.blazemc.connectiondetector.model.PremiumAccount;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatabasePersistenceTest {

    @TempDir
    Path tempDir;

    @Test
    void premiumReservationAndIdentitiesSurviveRestart() throws Exception {
        PluginConfig config = PluginConfig.defaults();
        UUID premiumUuid = UUID.fromString("11111111-1111-4111-8111-111111111111");
        UUID crackedUuid = UuidNormalizer.offlineUuid("Blaz3Craft");

        try (DatabaseManager database = new DatabaseManager(tempDir, config)) {
            database.open();
            SqlitePremiumAccountStore premium = new SqlitePremiumAccountStore(database);
            IdentityRepository identities = new IdentityRepository(database);

            identities.recordSuccessfulIdentity(VerifiedSession.from(
                    new LoginSnapshot("Blaz3Craft", crackedUuid, false, false, crackedUuid),
                    LoginDecision.allowCracked(crackedUuid)
            ));
            premium.insert(new PremiumAccount(
                    "Blaz3Craft",
                    premiumUuid,
                    Instant.now(),
                    null,
                    "Blaz3Craft",
                    Instant.now()
            ));
            identities.recordSuccessfulIdentity(VerifiedSession.from(
                    new LoginSnapshot("Blaz3Craft", premiumUuid, true, true, crackedUuid),
                    LoginDecision.allowPremium(
                            premium.findByUsername("Blaz3Craft").orElseThrow(),
                            premiumUuid
                    )
            ));
        }

        try (DatabaseManager restarted = new DatabaseManager(tempDir, config)) {
            restarted.open();
            SqlitePremiumAccountStore premium = new SqlitePremiumAccountStore(restarted);
            IdentityRepository identities = new IdentityRepository(restarted);

            PremiumAccount reserved = premium.findByUsername("Blaz3Craft").orElseThrow();
            assertEquals(premiumUuid, reserved.mojangUuid());

            List<IdentityRecord> records = identities.findByUsername("Blaz3Craft");
            assertEquals(2, records.size());
            assertTrue(records.stream().anyMatch(record ->
                    record.accountType() == AccountType.CRACKED && record.uuid().equals(crackedUuid)));
            assertTrue(records.stream().anyMatch(record ->
                    record.accountType() == AccountType.PREMIUM && record.uuid().equals(premiumUuid)));
            assertTrue(records.stream().noneMatch(record ->
                    record.accountType() == AccountType.PREMIUM && record.uuid().equals(crackedUuid)));
        }
    }

    @Test
    void existingCrackedProfileRemainsUntouchedWhenPremiumIsRegistered() throws Exception {
        PluginConfig config = PluginConfig.defaults();
        UUID crackedUuid = UuidNormalizer.offlineUuid("Blaz3Craft");
        UUID premiumUuid = UUID.fromString("11111111-1111-4111-8111-111111111111");

        try (DatabaseManager database = new DatabaseManager(tempDir, config)) {
            database.open();
            IdentityRepository identities = new IdentityRepository(database);
            identities.recordSuccessfulIdentity(VerifiedSession.from(
                    new LoginSnapshot("Blaz3Craft", crackedUuid, false, false, crackedUuid),
                    LoginDecision.allowCracked(crackedUuid)
            ));
            IdentityRecord before = identities.findByUsername("Blaz3Craft").get(0);

            SqlitePremiumAccountStore premium = new SqlitePremiumAccountStore(database);
            premium.insert(new PremiumAccount(
                    "Blaz3Craft",
                    premiumUuid,
                    Instant.now(),
                    null,
                    "Blaz3Craft",
                    Instant.now()
            ));

            IdentityRecord after = identities.findByUsername("Blaz3Craft").get(0);
            assertEquals(before.uuid(), after.uuid());
            assertEquals(AccountType.CRACKED, after.accountType());
            assertEquals(before.firstSeen(), after.firstSeen());
            assertEquals(1, identities.findByUsername("Blaz3Craft").size());
        }
    }
}
