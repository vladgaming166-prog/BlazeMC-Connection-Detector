package net.blazemc.connectiondetector.limboauth;

import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.PluginDescription;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.blazemc.connectiondetector.PluginConstants;
import net.blazemc.connectiondetector.config.PluginConfig;
import net.blazemc.connectiondetector.util.PluginLog;

/**
 * Optional LimboAuth 1.1.14 adapter.
 *
 * <p>Uses only documented public integration points discovered from the
 * LimboAuth 1.1.14 source:</p>
 * <ul>
 *   <li>{@code LimboAuth#cacheAuthUser(Player)} to skip cracked /register and /login</li>
 *   <li>{@code PreAuthorizationEvent} / {@code PreRegisterEvent} {@code Result.BYPASS}</li>
 * </ul>
 *
 * <p>The adapter never touches LimboAuth's database, password hashes, or
 * internal classes. Cracked players are left on LimboAuth's normal flow.</p>
 */
public final class LimboAuthCompatibilityAdapter {

    public static final String PLUGIN_ID = "limboauth";
    public static final String LIMBOAPI_ID = "limboapi";

    private final ProxyServer proxy;
    private final Object plugin;
    private final PluginLog log;
    private volatile PluginConfig config;
    private volatile boolean attached;
    private volatile String detectedVersion = "not loaded";
    private volatile String limboApiVersion = "not loaded";
    private Object limboAuthInstance;
    private Method cacheAuthUser;
    private final Set<UUID> verifiedPremium = ConcurrentHashMap.newKeySet();

    public LimboAuthCompatibilityAdapter(ProxyServer proxy, Object plugin, PluginLog log, PluginConfig config) {
        this.proxy = proxy;
        this.plugin = plugin;
        this.log = log;
        this.config = config;
    }

    public void updateConfig(PluginConfig config) {
        this.config = config;
    }

    public void attach() {
        detectedVersion = describe(PLUGIN_ID);
        limboApiVersion = describe(LIMBOAPI_ID);

        if (attached) {
            refreshInstance();
            return;
        }

        if (config.limboauth().isDisabled() || !config.cracked().useLimboauth()) {
            log.info("LimboAuth compatibility adapter is disabled by configuration.");
            return;
        }

        Optional<PluginContainer> container = proxy.getPluginManager().getPlugin(PLUGIN_ID);
        if (container.isEmpty()) {
            String message = "LimboAuth was not detected. Cracked /register and /login will not be coordinated.";
            if (config.limboauth().requirePlugin()) {
                log.warn(message);
            } else {
                log.info(message + " compatibility-mode=auto");
            }
            return;
        }

        limboAuthInstance = container.get().getInstance().orElse(null);
        if (limboAuthInstance == null) {
            log.warn("LimboAuth is loaded but its instance is not available yet.");
            return;
        }

        try {
            cacheAuthUser = limboAuthInstance.getClass().getMethod("cacheAuthUser", Player.class);
        } catch (NoSuchMethodException ex) {
            log.warn("LimboAuth is present but cacheAuthUser(Player) was not found. Bypass events will still be used.");
        }

        registerEventBypass("net.elytrium.limboauth.event.PreAuthorizationEvent");
        registerEventBypass("net.elytrium.limboauth.event.PreRegisterEvent");
        attached = true;
        log.info("LimboAuth compatibility adapter attached (LimboAuth "
                + detectedVersion + ", LimboAPI " + limboApiVersion
                + ", tested with LimboAuth " + PluginConstants.TESTED_LIMBOAUTH
                + " and LimboAPI " + PluginConstants.TESTED_LIMBOAPI + ").");
    }

    private void refreshInstance() {
        Optional<PluginContainer> container = proxy.getPluginManager().getPlugin(PLUGIN_ID);
        limboAuthInstance = container.flatMap(PluginContainer::getInstance).orElse(null);
        cacheAuthUser = null;
        if (limboAuthInstance == null) {
            return;
        }
        try {
            cacheAuthUser = limboAuthInstance.getClass().getMethod("cacheAuthUser", Player.class);
        } catch (NoSuchMethodException ignored) {
            // Bypass events remain registered from the original attach.
        }
    }

    public boolean attached() {
        return attached;
    }

    public String detectedVersion() {
        return detectedVersion;
    }

    public String limboApiVersion() {
        return limboApiVersion;
    }

    public void markVerifiedPremium(Player player) {
        if (!attached) {
            return;
        }
        verifiedPremium.add(player.getUniqueId());
        invokeCacheAuthUser(player);
    }

    public void notifyPremiumLogin(Player player) {
        if (!attached) {
            return;
        }
        verifiedPremium.add(player.getUniqueId());
        invokeCacheAuthUser(player);
    }

    public void clear(Player player) {
        verifiedPremium.remove(player.getUniqueId());
    }

    public boolean shouldBypass(Player player) {
        return attached && player.isOnlineMode() && verifiedPremium.contains(player.getUniqueId());
    }

    public static boolean shouldBypassLimboAuth(boolean verifiedPremiumSession) {
        return verifiedPremiumSession;
    }

    private void invokeCacheAuthUser(Player player) {
        if (cacheAuthUser == null || limboAuthInstance == null) {
            return;
        }
        try {
            cacheAuthUser.invoke(limboAuthInstance, player);
            log.debug(() -> "Called LimboAuth.cacheAuthUser for verified Premium player " + player.getUsername());
        } catch (ReflectiveOperationException ex) {
            log.warn("Unable to call LimboAuth.cacheAuthUser: " + ex.getMessage());
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void registerEventBypass(String className) {
        try {
            Class eventClass = Class.forName(className);
            Class<?> resultClass = Class.forName("net.elytrium.limboauth.event.TaskEvent$Result");
            Object bypass = Enum.valueOf((Class<Enum>) resultClass.asSubclass(Enum.class), "BYPASS");
            Method getPlayer = eventClass.getMethod("getPlayer");
            Method setResult = eventClass.getMethod("setResult", resultClass);

            EventManager events = proxy.getEventManager();
            events.register(plugin, eventClass, PostOrder.FIRST, event -> {
                try {
                    Player player = (Player) getPlayer.invoke(event);
                    if (shouldBypass(player)) {
                        setResult.invoke(event, bypass);
                        log.debug(() -> "Bypassed LimboAuth " + className + " for " + player.getUsername());
                    }
                } catch (ReflectiveOperationException ex) {
                    log.warn("LimboAuth event adapter failed: " + ex.getMessage());
                }
            });
        } catch (ClassNotFoundException ex) {
            log.debug(() -> "LimboAuth event class not present: " + className);
        } catch (ReflectiveOperationException ex) {
            log.warn("Unable to hook " + className + ": " + ex.getMessage());
        }
    }

    private String describe(String pluginId) {
        return proxy.getPluginManager().getPlugin(pluginId)
                .map(PluginContainer::getDescription)
                .map(PluginDescription::getVersion)
                .flatMap(version -> version)
                .orElse("not loaded");
    }
}
