package net.blazemc.connectiondetector.util;

import java.util.function.Supplier;
import net.blazemc.connectiondetector.PluginConstants;
import net.blazemc.connectiondetector.config.PluginConfig;
import org.slf4j.Logger;

public final class PluginLog {

    private final Logger logger;
    private volatile PluginConfig config;

    public PluginLog(Logger logger, PluginConfig config) {
        this.logger = logger;
        this.config = config;
    }

    public void updateConfig(PluginConfig config) {
        this.config = config;
    }

    public void info(String message) {
        logger.info(PluginConstants.LOG_PREFIX + message);
    }

    public void warn(String message) {
        logger.warn(PluginConstants.LOG_PREFIX + message);
    }

    public void error(String message, Throwable error) {
        logger.error(PluginConstants.LOG_PREFIX + message, error);
    }

    public void debug(Supplier<String> message) {
        if (config.debug()) {
            logger.info(PluginConstants.LOG_PREFIX + "[DEBUG] " + message.get());
        }
    }

    public void premiumDetected(String username, boolean autoDetected) {
        if (config.logging().logPremiumDetection()) {
            info((autoDetected ? "Automatic Premium profile detected: " : "Reserved Premium profile detected: ")
                    + username);
        }
    }

    public void forcingOnline(String username) {
        if (config.logging().logPremiumDetection()) {
            info("Forcing Mojang session authentication.");
            debug(() -> "Online authentication required for " + username);
        }
    }

    public void premiumVerified(String username) {
        if (config.logging().logPremiumAuthentication()) {
            info("Premium session successfully verified.");
            info("PREMIUM connection verified: " + username);
        }
    }

    public void uuidVerified(java.util.UUID uuid) {
        if (config.logging().logUuidVerification()) {
            info("Premium UUID verified: " + uuid);
            info("UUID verification successful.");
        }
    }

    public void crackedConfirmed(String username) {
        if (config.logging().logCrackedConnections()) {
            info("Cracked profile confirmed: " + username);
        }
    }

    public void cracked(String username) {
        crackedConfirmed(username);
    }

    public void impersonation(String username) {
        if (config.logging().logAuthenticationFailures()) {
            info("Premium impersonation attempt rejected.");
            debug(() -> "Rejected impersonation of " + username);
        }
    }

    public void mojangUnavailable(String username) {
        if (config.logging().logAuthenticationFailures()) {
            info("Mojang API unavailable. Connection rejected safely.");
            debug(() -> "Fail-closed reject for " + username);
        }
    }

    public void authFailure(String username) {
        if (config.logging().logAuthenticationFailures()) {
            info("Premium authentication failed: " + username);
        }
    }

    public Logger raw() {
        return logger;
    }
}
