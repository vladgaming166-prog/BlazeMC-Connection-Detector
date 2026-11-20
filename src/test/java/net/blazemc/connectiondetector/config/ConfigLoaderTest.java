package net.blazemc.connectiondetector.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesDocumentedSettings() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/config.yml"));
        PluginConfig config = new ConfigLoader(tempDir).parse(yaml);
        assertTrue(config.premiumDetection().forceOnlineAuthentication());
        assertTrue(config.premiumDetection().requireAuthenticatedSession());
        assertTrue(config.premiumDetection().verifyMojangUuid());
        assertTrue(config.premiumDetection().automatic());
        assertTrue(config.cracked().preserveOfflineUuid());
        assertTrue(config.cracked().useLimboauth());
        assertTrue(config.cracked().requireDefinitiveProfileNotFound());
        assertEquals(5000, config.mojang().apiTimeoutMs());
        assertEquals(300, config.mojang().positiveCacheSeconds());
        assertEquals(0, config.negativeCacheSeconds());
        assertEquals(2, config.mojang().retryCount());
        assertTrue(config.failClosed());
        assertEquals("sqlite", config.database().type());
        assertEquals("database/identities.db", config.database().file());
        assertEquals("auto", config.limboauth().compatibilityMode());
        assertTrue(config.logging().logPremiumDetection());
        assertTrue(config.logging().logAuthenticationFailures());
        assertEquals("en", config.general().language());
        assertTrue(config.uuid().neverAutoMigrate());
        assertTrue(config.uuid().preserveExistingCrackedIdentities());
    }

    @Test
    void writesDefaultConfigOnFirstLoad() throws Exception {
        ConfigLoader loader = new ConfigLoader(tempDir);
        PluginConfig config = loader.load();
        assertTrue(Files.exists(loader.configFile()));
        assertTrue(config.premiumDetection().enabled());
        assertTrue(config.premiumDetection().automatic());
    }

    @Test
    void legacyAuthenticationKeysStillParse() {
        String yaml = """
                authentication:
                  premium:
                    enabled: true
                    force-online-mode: true
                    require-mojang-session: true
                    verify-registered-uuid: true
                  cracked:
                    enabled: true
                    preserve-offline-mode: true
                    use-limboauth: true
                mojang:
                  cache-duration-minutes: 7
                  fail-closed-on-api-error: true
                """;
        PluginConfig config = new ConfigLoader(tempDir).parse(yaml);
        assertTrue(config.premiumDetection().forceOnlineAuthentication());
        assertTrue(config.premiumDetection().requireAuthenticatedSession());
        assertTrue(config.cracked().preserveOfflineUuid());
        assertEquals(420, config.mojang().positiveCacheSeconds());
        assertTrue(config.failClosed());
    }
}
