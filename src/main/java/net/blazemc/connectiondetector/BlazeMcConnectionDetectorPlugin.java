package net.blazemc.connectiondetector;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import net.blazemc.connectiondetector.auth.AuthenticationService;
import net.blazemc.connectiondetector.auth.CachedMojangApi;
import net.blazemc.connectiondetector.auth.ConcurrentLoginGuard;
import net.blazemc.connectiondetector.auth.HttpMojangApi;
import net.blazemc.connectiondetector.auth.IdentityInspector;
import net.blazemc.connectiondetector.auth.MojangApi;
import net.blazemc.connectiondetector.auth.MojangProfileCache;
import net.blazemc.connectiondetector.auth.PendingAuthRegistry;
import net.blazemc.connectiondetector.auth.PremiumRegistrationService;
import net.blazemc.connectiondetector.command.BcdCommand;
import net.blazemc.connectiondetector.config.ConfigLoader;
import net.blazemc.connectiondetector.config.PluginConfig;
import net.blazemc.connectiondetector.limboauth.LimboAuthCompatibilityAdapter;
import net.blazemc.connectiondetector.listener.ConnectionListener;
import net.blazemc.connectiondetector.storage.DatabaseManager;
import net.blazemc.connectiondetector.storage.IdentityRepository;
import net.blazemc.connectiondetector.storage.SqlitePremiumAccountStore;
import net.blazemc.connectiondetector.util.PluginLog;
import org.slf4j.Logger;

@Plugin(
        id = PluginConstants.ID,
        name = PluginConstants.NAME,
        version = PluginConstants.VERSION,
        description = "Automatic Premium detection, UUID protection, and hybrid Cracked authentication for BlazeMC Network",
        authors = {PluginConstants.AUTHOR},
        dependencies = {
                @Dependency(id = "limboauth", optional = true),
                @Dependency(id = "limboapi", optional = true)
        }
)
public final class BlazeMcConnectionDetectorPlugin {

    private final ProxyServer proxy;
    private final Path dataDirectory;
    private final PluginLog log;
    private final PluginServices services = new PluginServices();

    private ConfigLoader configLoader;
    private SqlitePremiumAccountStore premiumStore;
    private PendingAuthRegistry pendingAuth;
    private ConcurrentLoginGuard loginGuard;
    private MojangProfileCache mojangCache;

    @Inject
    public BlazeMcConnectionDetectorPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.dataDirectory = dataDirectory;
        this.log = new PluginLog(logger, PluginConfig.defaults());
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        try {
            start();
            log.info("Plugin enabled successfully.");
            log.info("Target Velocity API " + PluginConstants.TESTED_VELOCITY_API
                    + " compiled for Java 21, intended runtime Java 21-25.");
        } catch (Exception ex) {
            log.error("Failed to enable the plugin.", ex);
            throw new IllegalStateException("BlazeMC Connection Detector failed to start", ex);
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        shutdown();
        log.info("Plugin disabled.");
    }

    private void start() throws IOException, SQLException {
        configLoader = new ConfigLoader(dataDirectory);
        PluginConfig config = configLoader.load();
        services.setConfig(config);
        log.updateConfig(config);
        validateConfig(config);

        DatabaseManager database = new DatabaseManager(dataDirectory, config);
        database.open();
        services.setDatabase(database);

        premiumStore = new SqlitePremiumAccountStore(database);
        IdentityRepository identities = new IdentityRepository(database);
        services.setIdentities(identities);

        loginGuard = new ConcurrentLoginGuard();
        pendingAuth = new PendingAuthRegistry();
        rebuildAuthServices(config);

        LimboAuthCompatibilityAdapter limboAuth = new LimboAuthCompatibilityAdapter(proxy, this, log, config);
        services.setLimboAuth(limboAuth);

        ConnectionListener connectionListener = new ConnectionListener(
                services,
                identities,
                pendingAuth,
                limboAuth,
                log
        );
        services.setConnections(connectionListener);

        proxy.getEventManager().register(this, connectionListener);
        limboAuth.attach();
        registerCommand();
    }

    private void rebuildAuthServices(PluginConfig config) {
        services.setAuthentication(new AuthenticationService(premiumStore, config, loginGuard));
        Duration positive = Duration.ofSeconds(config.mojang().positiveCacheSeconds());
        Duration negative = Duration.ofSeconds(config.negativeCacheSeconds());
        if (mojangCache == null) {
            mojangCache = new MojangProfileCache(positive, negative);
        } else {
            mojangCache.updateTtl(positive, negative);
        }
        services.setMojangCache(mojangCache);
        MojangApi mojangApi = new CachedMojangApi(new HttpMojangApi(config), mojangCache);
        services.setMojangApi(mojangApi);
        services.setRegistration(new PremiumRegistrationService(premiumStore, mojangApi, config));
        services.setInspector(new IdentityInspector(premiumStore, services.identities()));
    }

    private void registerCommand() {
        CommandManager commands = proxy.getCommandManager();
        BcdCommand command = new BcdCommand(proxy, services, this::reload);
        commands.register(
                commands.metaBuilder("bcd")
                        .aliases("blazemcconnection", "connectiondetector")
                        .plugin(this)
                        .build(),
                command
        );
    }

    public void reload() {
        try {
            PluginConfig reloaded = configLoader.load();
            services.setConfig(reloaded);
            log.updateConfig(reloaded);
            rebuildAuthServices(reloaded);
            if (services.limboAuth() != null) {
                services.limboAuth().updateConfig(reloaded);
                services.limboAuth().attach();
            }
            validateConfig(reloaded);
            log.info("Configuration reloaded.");
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to reload config.yml", ex);
        }
    }

    private void validateConfig(PluginConfig config) {
        if (!"sqlite".equalsIgnoreCase(config.database().type())) {
            throw new IllegalStateException("Only SQLite is supported. Set database.type to sqlite.");
        }
        if (config.premiumDetection().enabled() && !config.premiumDetection().forceOnlineAuthentication()) {
            log.warn("Premium detection is enabled without force-online-authentication. Impersonation protection is weakened.");
        }
        if (config.premiumDetection().enabled() && !config.premiumDetection().requireAuthenticatedSession()) {
            log.warn("require-authenticated-session is disabled. Premium names may be accepted without a Mojang session.");
        }
        if (!config.failClosed()) {
            log.warn("Fail-closed security is disabled. Mojang API errors may allow unverified offline logins.");
        }
    }

    private void shutdown() {
        if (services.database() != null) {
            services.database().close();
        }
        if (pendingAuth != null) {
            pendingAuth.clear();
        }
        if (loginGuard != null) {
            loginGuard.clear();
        }
    }
}
