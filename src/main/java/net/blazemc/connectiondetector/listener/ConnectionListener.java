package net.blazemc.connectiondetector.listener;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.util.GameProfile;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.blazemc.connectiondetector.PluginServices;
import net.blazemc.connectiondetector.auth.AuthenticationService;
import net.blazemc.connectiondetector.auth.LoginDecision;
import net.blazemc.connectiondetector.auth.LoginSnapshot;
import net.blazemc.connectiondetector.auth.PendingAuthRegistry;
import net.blazemc.connectiondetector.auth.PendingAuthSession;
import net.blazemc.connectiondetector.auth.PreLoginDecision;
import net.blazemc.connectiondetector.auth.PremiumRegistrationService;
import net.blazemc.connectiondetector.auth.ProfileProtectionDecision;
import net.blazemc.connectiondetector.auth.ProfileSnapshot;
import net.blazemc.connectiondetector.auth.UuidNormalizer;
import net.blazemc.connectiondetector.auth.VerifiedSession;
import net.blazemc.connectiondetector.config.PluginConfig;
import net.blazemc.connectiondetector.limboauth.LimboAuthCompatibilityAdapter;
import net.blazemc.connectiondetector.model.AccountType;
import net.blazemc.connectiondetector.model.AuthenticationResult;
import net.blazemc.connectiondetector.storage.IdentityRepository;
import net.blazemc.connectiondetector.util.PluginLog;
import net.blazemc.connectiondetector.util.Texts;

public final class ConnectionListener {

    private final IdentityRepository identities;
    private final PendingAuthRegistry pendingAuth;
    private final LimboAuthCompatibilityAdapter limboAuth;
    private final PluginLog log;
    private final PluginServices services;
    private final ConcurrentHashMap<UUID, VerifiedSession> verifiedByPlayer = new ConcurrentHashMap<>();

    public ConnectionListener(
            PluginServices services,
            IdentityRepository identities,
            PendingAuthRegistry pendingAuth,
            LimboAuthCompatibilityAdapter limboAuth,
            PluginLog log
    ) {
        this.services = services;
        this.identities = identities;
        this.pendingAuth = pendingAuth;
        this.limboAuth = limboAuth;
        this.log = log;
    }

    private AuthenticationService authentication() {
        return services.authentication();
    }

    private PremiumRegistrationService registration() {
        return services.registration();
    }

    private PluginConfig config() {
        return services.config();
    }

    public VerifiedSession verifiedSession(Player player) {
        return verifiedByPlayer.get(player.getUniqueId());
    }

    @Subscribe(order = PostOrder.LAST)
    public void onPreLogin(PreLoginEvent event) {
        if (!event.getResult().isAllowed() && !event.getResult().isForceOfflineMode()
                && !event.getResult().isOnlineModeAllowed()) {
            return;
        }

        String username = event.getUsername();
        String connectionId = connectionId(event.getConnection());
        UUID claimed = event.getUniqueId();
        log.debug(() -> "PreLogin username=" + username
                + " claimedUuid=" + claimed
                + " (claimed UUIDs are never trusted)");

        PreLoginDecision decision = authentication().decidePreLogin(username, connectionId);
        pendingAuth.put(PendingAuthSession.from(connectionId, username, decision, claimed));

        switch (decision.action()) {
            case FORCE_ONLINE -> {
                event.setResult(PreLoginEvent.PreLoginComponentResult.forceOnlineMode());
                log.debug(() -> "Forcing online-mode authentication for reserved Premium username " + username);
            }
            case FORCE_OFFLINE -> event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
            case DENY -> {
                event.setResult(PreLoginEvent.PreLoginComponentResult.denied(kickMessage(decision.result())));
                authentication().releaseConnection(username, connectionId);
                pendingAuth.remove(connectionId);
                recordFailure(username, claimed, decision.result(), "prelogin");
            }
        }
    }

    @Subscribe(order = PostOrder.LAST)
    public void onGameProfileRequest(GameProfileRequestEvent event) {
        ProfileSnapshot snapshot = new ProfileSnapshot(
                event.getUsername(),
                event.getOriginalProfile().getId(),
                event.getGameProfile().getId(),
                event.isOnlineMode()
        );
        ProfileProtectionDecision decision = authentication().protectProfile(snapshot);
        if (decision.shouldLeaveUntouched() || decision.uuidToKeep().isEmpty()) {
            return;
        }

        UUID keep = decision.uuidToKeep().get();
        GameProfile current = event.getGameProfile();
        if (!keep.equals(current.getId())) {
            event.setGameProfile(current.withId(keep));
            log.debug(() -> "Restored Premium Mojang UUID for " + event.getUsername() + ": " + keep);
        }
    }

    @Subscribe(order = PostOrder.EARLY)
    public void onLogin(LoginEvent event) {
        Player player = event.getPlayer();
        String username = player.getUsername();
        String connectionId = pendingAuth.findByUsername(username)
                .map(PendingAuthSession::connectionId)
                .orElseGet(() -> "login:" + username);

        LoginSnapshot snapshot = new LoginSnapshot(
                username,
                player.getUniqueId(),
                player.isOnlineMode(),
                hasMojangSessionEvidence(event, player),
                pendingAuth.findByUsername(username).map(PendingAuthSession::clientClaimedUuid).orElse(null)
        );
        LoginDecision decision = authentication().verifyLogin(snapshot);
        authentication().releaseConnection(username, connectionId);
        pendingAuth.findByUsername(username).map(PendingAuthSession::connectionId).ifPresent(pendingAuth::remove);

        if (!decision.allowed()) {
            event.setResult(ResultedEvent.ComponentResult.denied(kickMessage(decision.result())));
            recordFailure(username, snapshot.uuid(), decision.result(), "login");
            log.authFailure(username);
            log.debug(() -> "Denied " + username + " result=" + decision.result()
                    + " uuid=" + snapshot.uuid()
                    + " onlineMode=" + snapshot.onlineMode());
            return;
        }

        VerifiedSession session = VerifiedSession.from(snapshot, decision);
        verifiedByPlayer.put(player.getUniqueId(), session);

        if (session.verifiedPremium()) {
            registration().recordSuccessfulAuth(session.uuid(), username);
            identities.recordSuccessfulIdentity(session);
            identities.recordHistory(session, "premium verified");
            limboAuth.markVerifiedPremium(player);
            log.premium(username);
            log.info("UUID verification successful.");
        } else if (config().authentication().cracked().enabled()) {
            identities.recordSuccessfulIdentity(session);
            identities.recordHistory(session, "cracked allowed");
            log.cracked(username);
        }
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        Player player = event.getPlayer();
        VerifiedSession session = verifiedByPlayer.get(player.getUniqueId());
        if (session != null && session.verifiedPremium()) {
            limboAuth.notifyPremiumLogin(player);
        }
        log.debug(() -> {
            if (session == null) {
                return "PostLogin without stored session for " + player.getUsername();
            }
            return "PostLogin " + player.getUsername()
                    + " type=" + session.accountType()
                    + " uuid=" + session.uuid()
                    + " forwarding=" + session.backendForwardingUuid()
                    + " onlineMode=" + player.isOnlineMode();
        });
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        verifiedByPlayer.remove(player.getUniqueId());
        pendingAuth.findByUsername(player.getUsername())
                .ifPresent(session -> {
                    authentication().releaseConnection(player.getUsername(), session.connectionId());
                    pendingAuth.remove(session.connectionId());
                });
        limboAuth.clear(player);
    }

    private void recordFailure(String username, UUID uuid, AuthenticationResult result, String stage) {
        identities.recordHistory(new net.blazemc.connectiondetector.model.ConnectionHistoryEntry(
                username,
                uuid,
                AccountType.PREMIUM,
                result,
                uuid == null ? net.blazemc.connectiondetector.model.UuidSource.UNKNOWN
                        : (UuidNormalizer.isOfflineStyleUuid(uuid, username)
                        ? net.blazemc.connectiondetector.model.UuidSource.OFFLINE
                        : net.blazemc.connectiondetector.model.UuidSource.UNKNOWN),
                authentication().findReserved(username).isPresent(),
                uuid,
                java.time.Instant.now(),
                "denied at " + stage
        ));
    }

    private net.kyori.adventure.text.Component kickMessage(AuthenticationResult result) {
        PluginConfig.Messages messages = config().messages();
        return switch (result) {
            case INVALID_USERNAME -> Texts.kick(messages.kickInvalidUsername());
            case CONCURRENT_LOGIN_DENIED -> Texts.kick(messages.kickConcurrentLogin());
            case PREMIUM_UUID_MISMATCH, PREMIUM_OFFLINE_UUID_REJECTED -> Texts.kick(messages.kickPremiumUuidMismatch());
            case PREMIUM_SESSION_MISSING -> Texts.kick(messages.kickPremiumSessionMissing());
            default -> Texts.kick(messages.kickPremiumAuthFailed());
        };
    }

    private static String connectionId(InboundConnection connection) {
        return Integer.toHexString(System.identityHashCode(connection));
    }

    /**
     * {@code LoginEvent#getServerIdHash()} exists on Velocity 3.5+. Older 3.x
     * builds only expose {@link Player#isOnlineMode()}, which is still a completed
     * Mojang session when true after {@code forceOnlineMode()}.
     */
    static boolean hasMojangSessionEvidence(LoginEvent event, Player player) {
        if (!player.isOnlineMode()) {
            return false;
        }
        try {
            Method method = event.getClass().getMethod("getServerIdHash");
            Object hash = method.invoke(event);
            if (hash == null) {
                return false;
            }
            String value = String.valueOf(hash);
            return !value.isBlank();
        } catch (NoSuchMethodException ignored) {
            return true;
        } catch (ReflectiveOperationException ignored) {
            return player.isOnlineMode();
        }
    }
}
