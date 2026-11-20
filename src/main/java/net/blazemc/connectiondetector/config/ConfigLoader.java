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
        Map<String, Object> premiumDetection = child(root, "premium-detection");
        Map<String, Object> legacyAuth = child(root, "authentication");
        Map<String, Object> legacyPremium = child(legacyAuth, "premium");
        Map<String, Object> legacyCracked = child(legacyAuth, "cracked");
        Map<String, Object> cracked = firstNonEmpty(child(root, "cracked"), legacyCracked);
        Map<String, Object> mojang = child(root, "mojang");
        Map<String, Object> database = child(root, "database");
        Map<String, Object> limboauth = child(root, "limboauth");
        Map<String, Object> uuid = child(root, "uuid");
        Map<String, Object> logging = child(root, "logging");
        Map<String, Object> messages = child(root, "messages");

        Map<String, String> messageValues = new LinkedHashMap<>(PluginConfig.Messages.defaults().values());
        for (Map.Entry<String, Object> entry : messages.entrySet()) {
            if (!"prefix".equals(entry.getKey()) && entry.getValue() != null) {
                messageValues.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }

        int positiveCacheSeconds = integer(mojang, "positive-cache-seconds", defaults.mojang().positiveCacheSeconds());
        if (!mojang.containsKey("positive-cache-seconds") && mojang.containsKey("cache-duration-minutes")) {
            positiveCacheSeconds = integer(mojang, "cache-duration-minutes", 5) * 60;
        }

        boolean failClosed = bool(
                premiumDetection,
                "fail-closed-on-api-error",
                bool(mojang, "fail-closed-on-api-error", defaults.premiumDetection().failClosedOnApiError())
        );

        return new PluginConfig(
                new PluginConfig.General(
                        bool(general, "debug", defaults.general().debug()),
                        string(general, "language", defaults.general().language()),
                        bool(general, "strict-security", defaults.general().strictSecurity())
                ),
                new PluginConfig.PremiumDetection(
                        bool(premiumDetection, "enabled", bool(legacyPremium, "enabled", true)),
                        bool(premiumDetection, "automatic", defaults.premiumDetection().automatic()),
                        bool(premiumDetection, "require-real-profile", defaults.premiumDetection().requireRealProfile()),
                        bool(premiumDetection, "require-authenticated-session",
                                bool(legacyPremium, "require-mojang-session", true)),
                        bool(premiumDetection, "verify-mojang-uuid",
                                bool(legacyPremium, "verify-registered-uuid", true)),
                        bool(premiumDetection, "force-online-authentication",
                                bool(legacyPremium, "force-online-mode", true)),
                        bool(premiumDetection, "never-downgrade-to-cracked", true),
                        bool(premiumDetection, "protect-premium-usernames", true),
                        failClosed
                ),
                new PluginConfig.Mojang(
                        integer(mojang, "api-timeout-ms", defaults.mojang().apiTimeoutMs()),
                        integer(mojang, "request-timeout-ms",
                                integer(mojang, "api-timeout-ms", defaults.mojang().requestTimeoutMs())),
                        positiveCacheSeconds,
                        integer(mojang, "negative-cache-seconds", defaults.mojang().negativeCacheSeconds()),
                        integer(mojang, "maximum-concurrent-requests", defaults.mojang().maximumConcurrentRequests()),
                        integer(mojang, "retry-count", defaults.mojang().retryCount())
                ),
                new PluginConfig.Cracked(
                        bool(cracked, "enabled", true),
                        bool(cracked, "require-definitive-profile-not-found", true),
                        bool(cracked, "preserve-offline-uuid", bool(cracked, "preserve-offline-mode", true)),
                        bool(cracked, "use-limboauth", true)
                ),
                new PluginConfig.LimboAuth(
                        bool(limboauth, "enabled", true),
                        string(limboauth, "integration-mode",
                                string(limboauth, "compatibility-mode", "auto")),
                        bool(limboauth, "prevent-premium-password-login", true)
                ),
                new PluginConfig.Uuid(
                        bool(uuid, "verify-backend-forwarding", true),
                        bool(uuid, "prevent-premium-offline-uuid", true),
                        bool(uuid, "preserve-existing-cracked-identities", true),
                        bool(uuid, "never-auto-migrate", true)
                ),
                new PluginConfig.Database(
                        string(database, "type", defaults.database().type()),
                        string(database, "file", defaults.database().file())
                ),
                new PluginConfig.Logging(
                        bool(logging, "log-premium-detection", true),
                        bool(logging, "log-premium-authentication",
                                bool(logging, "log-premium-connections", true)),
                        bool(logging, "log-cracked-connections", true),
                        bool(logging, "log-uuid-verification", true),
                        bool(logging, "log-authentication-failures", true)
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

    private static Map<String, Object> firstNonEmpty(Map<String, Object> primary, Map<String, Object> fallback) {
        return primary.isEmpty() ? fallback : primary;
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
