package net.blazemc.connectiondetector.listener;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.util.GameProfile;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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
import net.blazemc.connectiondetector.model.ConnectionState;
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
    public EventTask onPreLogin(PreLoginEvent event) {
        if (!event.getResult().isAllowed() && !event.getResult().isForceOfflineMode()
                && !event.getResult().isOnlineModeAllowed()) {
            return null;
        }

        String username = event.getUsername();
        String connectionId = connectionId(event.getConnection());
        UUID claimed = event.getUniqueId();
        log.debug(() -> "PreLogin username=" + username
                + " claimedUuid=" + claimed
                + " (claimed UUIDs are never trusted)");

        Optional<PreLoginDecision> immediate = authentication().tryImmediateDecision(username, connectionId);
        if (immediate.isPresent()) {
            applyPreLogin(event, username, connectionId, claimed, immediate.get());
            return null;
        }

        return EventTask.resumeWhenComplete(
                services.mojangApi().lookupByUsernameAsync(username)
                        .exceptionally(error -> net.blazemc.connectiondetector.auth.MojangLookupResult.error(
                                error.getMessage(), -1))
                        .thenAccept(lookup -> {
                            PreLoginDecision decision = authentication().decideFromLookup(
                                    username, connectionId, lookup);
                            applyPreLogin(event, username, connectionId, claimed, decision);
                        })
        );
    }

    private void applyPreLogin(
            PreLoginEvent event,
            String username,
            String connectionId,
            UUID claimed,
            PreLoginDecision decision
    ) {
        pendingAuth.put(PendingAuthSession.from(connectionId, username, decision, claimed));
        switch (decision.action()) {
            case FORCE_ONLINE -> {
                event.setResult(PreLoginEvent.PreLoginComponentResult.forceOnlineMode());
                log.premiumDetected(username, decision.autoDetected());
                log.forcingOnline(username);
            }
            case FORCE_OFFLINE -> {
                event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
                log.crackedConfirmed(username);
            }
            case DENY -> {
                event.setResult(PreLoginEvent.PreLoginComponentResult.denied(kickMessage(decision.result())));
                authentication().releaseConnection(username, connectionId);
                pendingAuth.remove(connectionId);
                recordFailure(username, claimed, decision.result(), "prelogin");
                if (decision.connectionState() == ConnectionState.MOJANG_API_UNAVAILABLE) {
                    log.mojangUnavailable(username);
                } else {
                    log.authFailure(username);
                }
            }
        }
    }

    @Subscribe(order = PostOrder.LAST)
    public void onGameProfileRequest(GameProfileRequestEvent event) {
        Optional<PendingAuthSession> pending = pendingAuth.findByUsername(event.getUsername());
        ProfileSnapshot snapshot = new ProfileSnapshot(
                event.getUsername(),
                event.getOriginalProfile().getId(),
                event.getGameProfile().getId(),
                event.isOnlineMode()
        );
        ProfileProtectionDecision decision = authentication().protectProfile(snapshot, pending);
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
        Optional<PendingAuthSession> pending = pendingAuth.findByUsername(username);
        String connectionId = pending.map(PendingAuthSession::connectionId).orElseGet(() -> "login:" + username);

        LoginSnapshot snapshot = new LoginSnapshot(
                username,
                player.getUniqueId(),
                player.isOnlineMode(),
                hasMojangSessionEvidence(event, player),
                pending.map(PendingAuthSession::clientClaimedUuid).orElse(null)
        );
        LoginDecision decision = authentication().verifyLogin(snapshot, pending);
        authentication().releaseConnection(username, connectionId);
        pending.map(PendingAuthSession::connectionId).ifPresent(pendingAuth::remove);

        if (!decision.allowed()) {
            event.setResult(ResultedEvent.ComponentResult.denied(kickMessage(decision.result())));
            recordFailure(username, snapshot.uuid(), decision.result(), "login");
            if (decision.result() == AuthenticationResult.IMPERSONATION_REJECTED
                    || decision.result() == AuthenticationResult.PREMIUM_SESSION_MISSING
                    || decision.result() == AuthenticationResult.PREMIUM_OFFLINE_UUID_REJECTED) {
                log.impersonation(username);
            } else {
                log.authFailure(username);
            }
            return;
        }

        VerifiedSession session = VerifiedSession.from(snapshot, decision, pending.orElse(null));
        if (config().uuid().verifyBackendForwarding()
                && session.verifiedPremium()
                && !session.uuid().equals(session.backendForwardingUuid())) {
            event.setResult(ResultedEvent.ComponentResult.denied(kickMessage(AuthenticationResult.PREMIUM_UUID_MISMATCH)));
            log.authFailure(username);
            return;
        }

        verifiedByPlayer.put(player.getUniqueId(), session);

        if (session.verifiedPremium()) {
            registration().persistVerified(username, session.uuid());
            if (config().uuid().preserveExistingCrackedIdentities()) {
                identities.recordSuccessfulIdentity(session);
            } else {
                identities.recordSuccessfulIdentity(session);
            }
            identities.recordHistory(session, session.autoDetected() ? "automatic premium verified" : "premium verified");
            if (config().limboauth().preventPremiumPasswordLogin()) {
                limboAuth.markVerifiedPremium(player);
            }
            log.premiumVerified(username);
            log.uuidVerified(session.uuid());
        } else if (config().cracked().enabled()) {
            identities.recordSuccessfulIdentity(session);
            identities.recordHistory(session, "cracked confirmed");
            log.cracked(username);
        }
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        Player player = event.getPlayer();
        VerifiedSession session = verifiedByPlayer.get(player.getUniqueId());
        if (session != null && session.verifiedPremium() && config().limboauth().preventPremiumPasswordLogin()) {
            limboAuth.notifyPremiumLogin(player);
        }
        log.debug(() -> {
            if (session == null) {
                return "PostLogin without stored session for " + player.getUsername();
            }
            return "PostLogin " + player.getUsername()
                    + " type=" + session.accountType()
                    + " state=" + session.connectionState()
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
            case PREMIUM_SESSION_MISSING, IMPERSONATION_REJECTED -> Texts.kick(messages.kickImpersonation());
            case MOJANG_API_UNAVAILABLE -> Texts.kick(messages.kickMojangUnavailable());
            default -> Texts.kick(messages.kickPremiumAuthFailed());
        };
    }

    private static String connectionId(InboundConnection connection) {
        return Integer.toHexString(System.identityHashCode(connection));
    }

    /**
     * {@code LoginEvent#getServerIdHash()} exists on Velocity 3.5+. Older 3.x
     * builds only expose {@link Player#isOnlineMode()}.
     */
    static boolean hasMojangSessionEvidence(LoginEvent event, Player player) {
        if (!player.isOnlineMode()) {
            return false;
        }
        try {
            Method method = event.getClass().getMethod("getServerIdHash");
            Object hash = method.invoke(event);
            // Additional Velocity 3.5+ evidence. An empty hash does not override
            // Player.isOnlineMode() after forceOnlineMode().
            return hash == null || !String.valueOf(hash).isBlank() || player.isOnlineMode();
        } catch (NoSuchMethodException ignored) {
            return true;
        } catch (ReflectiveOperationException ignored) {
            return true;
        }
    }
}
