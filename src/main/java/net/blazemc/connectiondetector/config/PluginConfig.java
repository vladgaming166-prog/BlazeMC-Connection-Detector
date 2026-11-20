package net.blazemc.connectiondetector.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PluginConfig {

    private final General general;
    private final PremiumDetection premiumDetection;
    private final Mojang mojang;
    private final Cracked cracked;
    private final LimboAuth limboauth;
    private final Uuid uuid;
    private final Database database;
    private final Logging logging;
    private final Messages messages;

    public PluginConfig(
            General general,
            PremiumDetection premiumDetection,
            Mojang mojang,
            Cracked cracked,
            LimboAuth limboauth,
            Uuid uuid,
            Database database,
            Logging logging,
            Messages messages
    ) {
        this.general = general;
        this.premiumDetection = premiumDetection;
        this.mojang = mojang;
        this.cracked = cracked;
        this.limboauth = limboauth;
        this.uuid = uuid;
        this.database = database;
        this.logging = logging;
        this.messages = messages;
    }

    public static PluginConfig defaults() {
        return new PluginConfig(
                new General(false, "en", true),
                PremiumDetection.defaults(),
                Mojang.defaults(),
                Cracked.defaults(),
                new LimboAuth(true, "auto", true),
                Uuid.defaults(),
                new Database("sqlite", "database/identities.db"),
                Logging.defaults(),
                Messages.defaults()
        );
    }

    public General general() {
        return general;
    }

    public PremiumDetection premiumDetection() {
        return premiumDetection;
    }

    public Mojang mojang() {
        return mojang;
    }

    public Cracked cracked() {
        return cracked;
    }

    public LimboAuth limboauth() {
        return limboauth;
    }

    public Uuid uuid() {
        return uuid;
    }

    public Database database() {
        return database;
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

    public boolean failClosed() {
        return general.strictSecurity() || premiumDetection.failClosedOnApiError();
    }

    public int negativeCacheSeconds() {
        if (general.strictSecurity()) {
            return 0;
        }
        return mojang.negativeCacheSeconds();
    }

    public static final class General {
        private final boolean debug;
        private final String language;
        private final boolean strictSecurity;

        public General(boolean debug, String language, boolean strictSecurity) {
            this.debug = debug;
            this.language = language == null || language.isBlank() ? "en" : language;
            this.strictSecurity = strictSecurity;
        }

        public boolean debug() {
            return debug;
        }

        public String language() {
            return language;
        }

        public boolean strictSecurity() {
            return strictSecurity;
        }
    }

    public static final class PremiumDetection {
        private final boolean enabled;
        private final boolean automatic;
        private final boolean requireRealProfile;
        private final boolean requireAuthenticatedSession;
        private final boolean verifyMojangUuid;
        private final boolean forceOnlineAuthentication;
        private final boolean neverDowngradeToCracked;
        private final boolean protectPremiumUsernames;
        private final boolean failClosedOnApiError;

        public PremiumDetection(
                boolean enabled,
                boolean automatic,
                boolean requireRealProfile,
                boolean requireAuthenticatedSession,
                boolean verifyMojangUuid,
                boolean forceOnlineAuthentication,
                boolean neverDowngradeToCracked,
                boolean protectPremiumUsernames,
                boolean failClosedOnApiError
        ) {
            this.enabled = enabled;
            this.automatic = automatic;
            this.requireRealProfile = requireRealProfile;
            this.requireAuthenticatedSession = requireAuthenticatedSession;
            this.verifyMojangUuid = verifyMojangUuid;
            this.forceOnlineAuthentication = forceOnlineAuthentication;
            this.neverDowngradeToCracked = neverDowngradeToCracked;
            this.protectPremiumUsernames = protectPremiumUsernames;
            this.failClosedOnApiError = failClosedOnApiError;
        }

        public static PremiumDetection defaults() {
            return new PremiumDetection(true, true, true, true, true, true, true, true, true);
        }

        public boolean enabled() {
            return enabled;
        }

        public boolean automatic() {
            return automatic;
        }

        public boolean requireRealProfile() {
            return requireRealProfile;
        }

        public boolean requireAuthenticatedSession() {
            return requireAuthenticatedSession;
        }

        public boolean verifyMojangUuid() {
            return verifyMojangUuid;
        }

        public boolean forceOnlineAuthentication() {
            return forceOnlineAuthentication;
        }

        public boolean neverDowngradeToCracked() {
            return neverDowngradeToCracked;
        }

        public boolean protectPremiumUsernames() {
            return protectPremiumUsernames;
        }

        public boolean failClosedOnApiError() {
            return failClosedOnApiError;
        }
    }

    public static final class Mojang {
        private final int apiTimeoutMs;
        private final int requestTimeoutMs;
        private final int positiveCacheSeconds;
        private final int negativeCacheSeconds;
        private final int maximumConcurrentRequests;
        private final int retryCount;

        public Mojang(
                int apiTimeoutMs,
                int requestTimeoutMs,
                int positiveCacheSeconds,
                int negativeCacheSeconds,
                int maximumConcurrentRequests,
                int retryCount
        ) {
            this.apiTimeoutMs = Math.max(500, apiTimeoutMs);
            this.requestTimeoutMs = Math.max(500, requestTimeoutMs);
            this.positiveCacheSeconds = Math.max(0, positiveCacheSeconds);
            this.negativeCacheSeconds = Math.max(0, negativeCacheSeconds);
            this.maximumConcurrentRequests = Math.max(1, maximumConcurrentRequests);
            this.retryCount = Math.max(0, retryCount);
        }

        public static Mojang defaults() {
            return new Mojang(5000, 5000, 300, 0, 20, 2);
        }

        public int apiTimeoutMs() {
            return apiTimeoutMs;
        }

        public int requestTimeoutMs() {
            return requestTimeoutMs;
        }

        public int positiveCacheSeconds() {
            return positiveCacheSeconds;
        }

        public int negativeCacheSeconds() {
            return negativeCacheSeconds;
        }

        public int maximumConcurrentRequests() {
            return maximumConcurrentRequests;
        }

        public int retryCount() {
            return retryCount;
        }
    }

    public static final class Cracked {
        private final boolean enabled;
        private final boolean requireDefinitiveProfileNotFound;
        private final boolean preserveOfflineUuid;
        private final boolean useLimboauth;

        public Cracked(
                boolean enabled,
                boolean requireDefinitiveProfileNotFound,
                boolean preserveOfflineUuid,
                boolean useLimboauth
        ) {
            this.enabled = enabled;
            this.requireDefinitiveProfileNotFound = requireDefinitiveProfileNotFound;
            this.preserveOfflineUuid = preserveOfflineUuid;
            this.useLimboauth = useLimboauth;
        }

        public static Cracked defaults() {
            return new Cracked(true, true, true, true);
        }

        public boolean enabled() {
            return enabled;
        }

        public boolean requireDefinitiveProfileNotFound() {
            return requireDefinitiveProfileNotFound;
        }

        public boolean preserveOfflineUuid() {
            return preserveOfflineUuid;
        }

        public boolean useLimboauth() {
            return useLimboauth;
        }
    }

    public static final class LimboAuth {
        private final boolean enabled;
        private final String integrationMode;
        private final boolean preventPremiumPasswordLogin;

        public LimboAuth(boolean enabled, String integrationMode, boolean preventPremiumPasswordLogin) {
            this.enabled = enabled;
            this.integrationMode = integrationMode == null || integrationMode.isBlank()
                    ? "auto"
                    : integrationMode.toLowerCase();
            this.preventPremiumPasswordLogin = preventPremiumPasswordLogin;
        }

        public boolean enabled() {
            return enabled;
        }

        public String integrationMode() {
            return integrationMode;
        }

        public String compatibilityMode() {
            return integrationMode;
        }

        public boolean preventPremiumPasswordLogin() {
            return preventPremiumPasswordLogin;
        }

        public boolean isDisabled() {
            return !enabled || "disabled".equals(integrationMode);
        }

        public boolean requirePlugin() {
            return enabled && "enabled".equals(integrationMode);
        }
    }

    public static final class Uuid {
        private final boolean verifyBackendForwarding;
        private final boolean preventPremiumOfflineUuid;
        private final boolean preserveExistingCrackedIdentities;
        private final boolean neverAutoMigrate;

        public Uuid(
                boolean verifyBackendForwarding,
                boolean preventPremiumOfflineUuid,
                boolean preserveExistingCrackedIdentities,
                boolean neverAutoMigrate
        ) {
            this.verifyBackendForwarding = verifyBackendForwarding;
            this.preventPremiumOfflineUuid = preventPremiumOfflineUuid;
            this.preserveExistingCrackedIdentities = preserveExistingCrackedIdentities;
            this.neverAutoMigrate = neverAutoMigrate;
        }

        public static Uuid defaults() {
            return new Uuid(true, true, true, true);
        }

        public boolean verifyBackendForwarding() {
            return verifyBackendForwarding;
        }

        public boolean preventPremiumOfflineUuid() {
            return preventPremiumOfflineUuid;
        }

        public boolean preserveExistingCrackedIdentities() {
            return preserveExistingCrackedIdentities;
        }

        public boolean neverAutoMigrate() {
            return neverAutoMigrate;
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

    public static final class Logging {
        private final boolean logPremiumDetection;
        private final boolean logPremiumAuthentication;
        private final boolean logCrackedConnections;
        private final boolean logUuidVerification;
        private final boolean logAuthenticationFailures;

        public Logging(
                boolean logPremiumDetection,
                boolean logPremiumAuthentication,
                boolean logCrackedConnections,
                boolean logUuidVerification,
                boolean logAuthenticationFailures
        ) {
            this.logPremiumDetection = logPremiumDetection;
            this.logPremiumAuthentication = logPremiumAuthentication;
            this.logCrackedConnections = logCrackedConnections;
            this.logUuidVerification = logUuidVerification;
            this.logAuthenticationFailures = logAuthenticationFailures;
        }

        public static Logging defaults() {
            return new Logging(true, true, true, true, true);
        }

        public boolean logPremiumDetection() {
            return logPremiumDetection;
        }

        public boolean logPremiumAuthentication() {
            return logPremiumAuthentication;
        }

        public boolean logCrackedConnections() {
            return logCrackedConnections;
        }

        public boolean logUuidVerification() {
            return logUuidVerification;
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
                    "This username belongs to a Minecraft Premium account. Sign in with the genuine Microsoft account."
            );
            values.put("kick-premium-uuid-mismatch", "Premium UUID verification failed. This username is protected.");
            values.put(
                    "kick-premium-auth-failed",
                    "Premium authentication failed. This username cannot be used in offline mode."
            );
            values.put("kick-concurrent-login", "A connection for this Premium account is already in progress.");
            values.put(
                    "kick-mojang-unavailable",
                    "Authentication servers are temporarily unavailable. Please retry in a moment."
            );
            values.put(
                    "kick-impersonation",
                    "This username is protected. Cracked launchers cannot use Premium identities."
            );
            return new Messages("[BlazeMC Connection Detector] ", values);
        }

        public String prefix() {
            return prefix;
        }

        public Map<String, String> values() {
            return values;
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

        public String kickMojangUnavailable() {
            return get("kick-mojang-unavailable");
        }

        public String kickImpersonation() {
            return get("kick-impersonation");
        }
    }
}
