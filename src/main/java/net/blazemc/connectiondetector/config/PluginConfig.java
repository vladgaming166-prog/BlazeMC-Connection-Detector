package net.blazemc.connectiondetector.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class PluginConfig {

    private final General general;
    private final Authentication authentication;
    private final Mojang mojang;
    private final Database database;
    private final LimboAuth limboauth;
    private final Logging logging;
    private final Messages messages;

    public PluginConfig(
            General general,
            Authentication authentication,
            Mojang mojang,
            Database database,
            LimboAuth limboauth,
            Logging logging,
            Messages messages
    ) {
        this.general = general;
        this.authentication = authentication;
        this.mojang = mojang;
        this.database = database;
        this.limboauth = limboauth;
        this.logging = logging;
        this.messages = messages;
    }

    public static PluginConfig defaults() {
        return new PluginConfig(
                new General(false, "en"),
                new Authentication(
                        new Premium(true, true, true, true),
                        new Cracked(true, true, true)
                ),
                new Mojang(5000, 60, 2, true),
                new Database("sqlite", "database/identities.db"),
                new LimboAuth(true, "auto"),
                new Logging(true, true, true),
                Messages.defaults()
        );
    }

    public General general() {
        return general;
    }

    public Authentication authentication() {
        return authentication;
    }

    public Mojang mojang() {
        return mojang;
    }

    public Database database() {
        return database;
    }

    public LimboAuth limboauth() {
        return limboauth;
    }

    public Logging logging() {
        return logging;
    }

    public Messages messages() {
        return messages;
    }

    public boolean debug() {
        return general.debug();
    }

    public static final class General {
        private final boolean debug;
        private final String language;

        public General(boolean debug, String language) {
            this.debug = debug;
            this.language = language == null || language.isBlank() ? "en" : language;
        }

        public boolean debug() {
            return debug;
        }

        public String language() {
            return language;
        }
    }

    public static final class Authentication {
        private final Premium premium;
        private final Cracked cracked;

        public Authentication(Premium premium, Cracked cracked) {
            this.premium = premium;
            this.cracked = cracked;
        }

        public Premium premium() {
            return premium;
        }

        public Cracked cracked() {
            return cracked;
        }
    }

    public static final class Premium {
        private final boolean enabled;
        private final boolean forceOnlineMode;
        private final boolean requireMojangSession;
        private final boolean verifyRegisteredUuid;

        public Premium(
                boolean enabled,
                boolean forceOnlineMode,
                boolean requireMojangSession,
                boolean verifyRegisteredUuid
        ) {
            this.enabled = enabled;
            this.forceOnlineMode = forceOnlineMode;
            this.requireMojangSession = requireMojangSession;
            this.verifyRegisteredUuid = verifyRegisteredUuid;
        }

        public boolean enabled() {
            return enabled;
        }

        public boolean forceOnlineMode() {
            return forceOnlineMode;
        }

        public boolean requireMojangSession() {
            return requireMojangSession;
        }

        public boolean verifyRegisteredUuid() {
            return verifyRegisteredUuid;
        }
    }

    public static final class Cracked {
        private final boolean enabled;
        private final boolean preserveOfflineMode;
        private final boolean useLimboauth;

        public Cracked(boolean enabled, boolean preserveOfflineMode, boolean useLimboauth) {
            this.enabled = enabled;
            this.preserveOfflineMode = preserveOfflineMode;
            this.useLimboauth = useLimboauth;
        }

        public boolean enabled() {
            return enabled;
        }

        public boolean preserveOfflineMode() {
            return preserveOfflineMode;
        }

        public boolean useLimboauth() {
            return useLimboauth;
        }
    }

    public static final class Mojang {
        private final int apiTimeoutMs;
        private final int cacheDurationMinutes;
        private final int retryCount;
        private final boolean failClosedOnApiError;

        public Mojang(int apiTimeoutMs, int cacheDurationMinutes, int retryCount, boolean failClosedOnApiError) {
            this.apiTimeoutMs = Math.max(500, apiTimeoutMs);
            this.cacheDurationMinutes = Math.max(0, cacheDurationMinutes);
            this.retryCount = Math.max(0, retryCount);
            this.failClosedOnApiError = failClosedOnApiError;
        }

        public int apiTimeoutMs() {
            return apiTimeoutMs;
        }

        public int cacheDurationMinutes() {
            return cacheDurationMinutes;
        }

        public int retryCount() {
            return retryCount;
        }

        public boolean failClosedOnApiError() {
            return failClosedOnApiError;
        }
    }

    public static final class Database {
        private final String type;
        private final String file;

        public Database(String type, String file) {
            this.type = type == null || type.isBlank() ? "sqlite" : type;
            this.file = file == null || file.isBlank() ? "database/identities.db" : file;
        }

        public String type() {
            return type;
        }

        public String file() {
            return file;
        }
    }

    public static final class LimboAuth {
        private final boolean enabled;
        private final String compatibilityMode;

        public LimboAuth(boolean enabled, String compatibilityMode) {
            this.enabled = enabled;
            this.compatibilityMode = compatibilityMode == null || compatibilityMode.isBlank()
                    ? "auto"
                    : compatibilityMode.toLowerCase();
        }

        public boolean enabled() {
            return enabled;
        }

        public String compatibilityMode() {
            return compatibilityMode;
        }

        public boolean isDisabled() {
            return !enabled || "disabled".equals(compatibilityMode);
        }

        public boolean requirePlugin() {
            return enabled && "enabled".equals(compatibilityMode);
        }
    }

    public static final class Logging {
        private final boolean logPremiumConnections;
        private final boolean logCrackedConnections;
        private final boolean logAuthenticationFailures;

        public Logging(
                boolean logPremiumConnections,
                boolean logCrackedConnections,
                boolean logAuthenticationFailures
        ) {
            this.logPremiumConnections = logPremiumConnections;
            this.logCrackedConnections = logCrackedConnections;
            this.logAuthenticationFailures = logAuthenticationFailures;
        }

        public boolean logPremiumConnections() {
            return logPremiumConnections;
        }

        public boolean logCrackedConnections() {
            return logCrackedConnections;
        }

        public boolean logAuthenticationFailures() {
            return logAuthenticationFailures;
        }
    }

    public static final class Messages {
        private final String prefix;
        private final Map<String, String> values;

        public Messages(String prefix, Map<String, String> values) {
            this.prefix = prefix == null ? "[BlazeMC Connection Detector] " : prefix;
            this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        }

        public static Messages defaults() {
            Map<String, String> values = new LinkedHashMap<>();
            values.put("kick-invalid-username", "Invalid Minecraft username.");
            values.put(
                    "kick-premium-session-missing",
                    "This username is reserved for a Minecraft Premium account. Sign in with the genuine Microsoft account."
            );
            values.put("kick-premium-uuid-mismatch", "Premium UUID verification failed. This username is protected.");
            values.put(
                    "kick-premium-auth-failed",
                    "Premium authentication failed. This username cannot be used in offline mode."
            );
            values.put("kick-concurrent-login", "A connection for this Premium account is already in progress.");
            return new Messages("[BlazeMC Connection Detector] ", values);
        }

        public String prefix() {
            return prefix;
        }

        public String get(String key) {
            return values.getOrDefault(key, key);
        }

        public String kickInvalidUsername() {
            return get("kick-invalid-username");
        }

        public String kickPremiumSessionMissing() {
            return get("kick-premium-session-missing");
        }

        public String kickPremiumUuidMismatch() {
            return get("kick-premium-uuid-mismatch");
        }

        public String kickPremiumAuthFailed() {
            return get("kick-premium-auth-failed");
        }

        public String kickConcurrentLogin() {
            return get("kick-concurrent-login");
        }
    }

    public PluginConfig withGeneral(General updated) {
        return new PluginConfig(
                Objects.requireNonNull(updated),
                authentication,
                mojang,
                database,
                limboauth,
                logging,
                messages
        );
    }
}
