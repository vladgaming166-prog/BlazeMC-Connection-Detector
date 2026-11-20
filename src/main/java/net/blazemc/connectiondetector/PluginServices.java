package net.blazemc.connectiondetector;

import net.blazemc.connectiondetector.auth.AuthenticationService;
import net.blazemc.connectiondetector.auth.IdentityInspector;
import net.blazemc.connectiondetector.auth.MojangApi;
import net.blazemc.connectiondetector.auth.MojangProfileCache;
import net.blazemc.connectiondetector.auth.PremiumRegistrationService;
import net.blazemc.connectiondetector.config.PluginConfig;
import net.blazemc.connectiondetector.limboauth.LimboAuthCompatibilityAdapter;
import net.blazemc.connectiondetector.listener.ConnectionListener;
import net.blazemc.connectiondetector.storage.DatabaseManager;
import net.blazemc.connectiondetector.storage.IdentityRepository;

public final class PluginServices {

    private volatile PluginConfig config;
    private volatile AuthenticationService authentication;
    private volatile PremiumRegistrationService registration;
    private volatile IdentityInspector inspector;
    private volatile MojangApi mojangApi;
    private volatile MojangProfileCache mojangCache;
    private DatabaseManager database;
    private IdentityRepository identities;
    private ConnectionListener connections;
    private LimboAuthCompatibilityAdapter limboAuth;

    public PluginConfig config() {
        return config;
    }

    public void setConfig(PluginConfig config) {
        this.config = config;
    }

    public AuthenticationService authentication() {
        return authentication;
    }

    public void setAuthentication(AuthenticationService authentication) {
        this.authentication = authentication;
    }

    public PremiumRegistrationService registration() {
        return registration;
    }

    public void setRegistration(PremiumRegistrationService registration) {
        this.registration = registration;
    }

    public IdentityInspector inspector() {
        return inspector;
    }

    public void setInspector(IdentityInspector inspector) {
        this.inspector = inspector;
    }

    public MojangApi mojangApi() {
        return mojangApi;
    }

    public void setMojangApi(MojangApi mojangApi) {
        this.mojangApi = mojangApi;
    }

    public MojangProfileCache mojangCache() {
        return mojangCache;
    }

    public void setMojangCache(MojangProfileCache mojangCache) {
        this.mojangCache = mojangCache;
    }

    public DatabaseManager database() {
        return database;
    }

    public void setDatabase(DatabaseManager database) {
        this.database = database;
    }

    public IdentityRepository identities() {
        return identities;
    }

    public void setIdentities(IdentityRepository identities) {
        this.identities = identities;
    }

    public ConnectionListener connections() {
        return connections;
    }

    public void setConnections(ConnectionListener connections) {
        this.connections = connections;
    }

    public LimboAuthCompatibilityAdapter limboAuth() {
        return limboAuth;
    }

    public void setLimboAuth(LimboAuthCompatibilityAdapter limboAuth) {
        this.limboAuth = limboAuth;
    }
}
