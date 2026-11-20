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
        assertTrue(config.authentication().premium().forceOnlineMode());
        assertTrue(config.authentication().premium().requireMojangSession());
        assertTrue(config.authentication().premium().verifyRegisteredUuid());
        assertTrue(config.authentication().cracked().preserveOfflineMode());
        assertTrue(config.authentication().cracked().useLimboauth());
        assertEquals(5000, config.mojang().apiTimeoutMs());
        assertEquals(60, config.mojang().cacheDurationMinutes());
        assertEquals(2, config.mojang().retryCount());
        assertTrue(config.mojang().failClosedOnApiError());
        assertEquals("sqlite", config.database().type());
        assertEquals("database/identities.db", config.database().file());
        assertEquals("auto", config.limboauth().compatibilityMode());
        assertTrue(config.logging().logPremiumConnections());
        assertTrue(config.logging().logAuthenticationFailures());
        assertEquals("en", config.general().language());
    }

    @Test
    void writesDefaultConfigOnFirstLoad() throws Exception {
        ConfigLoader loader = new ConfigLoader(tempDir);
        PluginConfig config = loader.load();
        assertTrue(Files.exists(loader.configFile()));
        assertTrue(config.authentication().premium().enabled());
    }
}
