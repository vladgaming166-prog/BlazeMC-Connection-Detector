package net.blazemc.connectiondetector.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.blazemc.connectiondetector.PluginConstants;
import org.yaml.snakeyaml.Yaml;

public final class ConfigLoader {

    private static final String RESOURCE = "config.yml";

    private final Path dataDirectory;
    private final Path configFile;

    public ConfigLoader(Path dataDirectory) {
        this.dataDirectory = dataDirectory;
        this.configFile = dataDirectory.resolve("config.yml");
    }

    public Path configFile() {
        return configFile;
    }

    public PluginConfig load() throws IOException {
        Files.createDirectories(dataDirectory);
        if (!Files.exists(configFile)) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(RESOURCE)) {
                if (in == null) {
                    throw new IOException("Bundled " + RESOURCE + " is missing from the plugin JAR.");
                }
                Files.copy(in, configFile);
            }
        }
        return parse(Files.readString(configFile));
    }

    @SuppressWarnings("unchecked")
    public PluginConfig parse(String yamlText) {
        Yaml yaml = new Yaml();
        Object loaded = yaml.load(yamlText);
        Map<String, Object> root = loaded instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();

        PluginConfig defaults = PluginConfig.defaults();
        Map<String, Object> general = child(root, "general");
        Map<String, Object> authentication = child(root, "authentication");
        Map<String, Object> premium = child(authentication, "premium");
        Map<String, Object> cracked = child(authentication, "cracked");
        Map<String, Object> mojang = child(root, "mojang");
        Map<String, Object> database = child(root, "database");
        Map<String, Object> limboauth = child(root, "limboauth");
        Map<String, Object> logging = child(root, "logging");
        Map<String, Object> messages = child(root, "messages");

        Map<String, String> messageValues = new LinkedHashMap<>();
        messageValues.put("kick-invalid-username", defaults.messages().kickInvalidUsername());
        messageValues.put("kick-premium-session-missing", defaults.messages().kickPremiumSessionMissing());
        messageValues.put("kick-premium-uuid-mismatch", defaults.messages().kickPremiumUuidMismatch());
        messageValues.put("kick-premium-auth-failed", defaults.messages().kickPremiumAuthFailed());
        messageValues.put("kick-concurrent-login", defaults.messages().kickConcurrentLogin());
        for (Map.Entry<String, Object> entry : messages.entrySet()) {
            if (!"prefix".equals(entry.getKey()) && entry.getValue() != null) {
                messageValues.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }

        return new PluginConfig(
                new PluginConfig.General(
                        bool(general, "debug", defaults.general().debug()),
                        string(general, "language", defaults.general().language())
                ),
                new PluginConfig.Authentication(
                        new PluginConfig.Premium(
                                bool(premium, "enabled", defaults.authentication().premium().enabled()),
                                bool(premium, "force-online-mode", defaults.authentication().premium().forceOnlineMode()),
                                bool(premium, "require-mojang-session", defaults.authentication().premium().requireMojangSession()),
                                bool(premium, "verify-registered-uuid", defaults.authentication().premium().verifyRegisteredUuid())
                        ),
                        new PluginConfig.Cracked(
                                bool(cracked, "enabled", defaults.authentication().cracked().enabled()),
                                bool(cracked, "preserve-offline-mode", defaults.authentication().cracked().preserveOfflineMode()),
                                bool(cracked, "use-limboauth", defaults.authentication().cracked().useLimboauth())
                        )
                ),
                new PluginConfig.Mojang(
                        integer(mojang, "api-timeout-ms", defaults.mojang().apiTimeoutMs()),
                        integer(mojang, "cache-duration-minutes", defaults.mojang().cacheDurationMinutes()),
                        integer(mojang, "retry-count", defaults.mojang().retryCount()),
                        bool(mojang, "fail-closed-on-api-error", defaults.mojang().failClosedOnApiError())
                ),
                new PluginConfig.Database(
                        string(database, "type", defaults.database().type()),
                        string(database, "file", defaults.database().file())
                ),
                new PluginConfig.LimboAuth(
                        bool(limboauth, "enabled", defaults.limboauth().enabled()),
                        string(limboauth, "compatibility-mode", defaults.limboauth().compatibilityMode())
                ),
                new PluginConfig.Logging(
                        bool(logging, "log-premium-connections", defaults.logging().logPremiumConnections()),
                        bool(logging, "log-cracked-connections", defaults.logging().logCrackedConnections()),
                        bool(logging, "log-authentication-failures", defaults.logging().logAuthenticationFailures())
                ),
                new PluginConfig.Messages(
                        string(messages, "prefix", PluginConstants.LOG_PREFIX),
                        messageValues
                )
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> child(Map<String, Object> parent, String key) {
        Object value = parent.get(key);
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private static boolean bool(Map<String, Object> map, String key, boolean fallback) {
        Object value = map.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value != null) {
            return Boolean.parseBoolean(String.valueOf(value));
        }
        return fallback;
    }

    private static int integer(Map<String, Object> map, String key, int fallback) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static String string(Map<String, Object> map, String key, String fallback) {
        Object value = map.get(key);
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? fallback : text;
    }
}
