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

    public void premium(String username) {
        if (config.logging().logPremiumConnections()) {
            info("PREMIUM connection verified: " + username);
        }
    }

    public void cracked(String username) {
        if (config.logging().logCrackedConnections()) {
            info("CRACKED connection detected: " + username);
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
