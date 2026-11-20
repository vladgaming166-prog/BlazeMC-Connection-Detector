package net.blazemc.connectiondetector.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.blazemc.connectiondetector.PluginConstants;
import net.blazemc.connectiondetector.PluginServices;
import net.blazemc.connectiondetector.auth.ConfirmationStore;
import net.blazemc.connectiondetector.auth.IdentityInspector;
import net.blazemc.connectiondetector.auth.PremiumRegistrationService;
import net.blazemc.connectiondetector.auth.UuidNormalizer;
import net.blazemc.connectiondetector.auth.VerifiedSession;
import net.blazemc.connectiondetector.config.PluginConfig;
import net.blazemc.connectiondetector.model.IdentityRecord;
import net.blazemc.connectiondetector.model.PremiumAccount;
import net.blazemc.connectiondetector.util.Texts;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class BcdCommand implements SimpleCommand {

    private final ProxyServer proxy;
    private final PluginServices services;
    private final ConfirmationStore confirmations = new ConfirmationStore();
    private final Runnable reloadAction;

    public BcdCommand(ProxyServer proxy, PluginServices services, Runnable reloadAction) {
        this.proxy = proxy;
        this.services = services;
        this.reloadAction = reloadAction;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        if (args.length == 0) {
            sendHelp(source);
            return;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> reload(source);
            case "status" -> status(source);
            case "debug" -> debug(source, args);
            case "lookup" -> lookup(source, args);
            case "check" -> check(source, args);
            case "cache" -> cache(source, args);
            case "diagnostics" -> diagnostics(source, args);
            case "premium" -> premium(source, args);
            default -> source.sendMessage(Texts.error("Unknown subcommand. Use /bcd for help."));
        }
    }

    private void sendHelp(CommandSource source) {
        source.sendMessage(Texts.info(PluginConstants.NAME + " v" + PluginConstants.VERSION));
        source.sendMessage(Component.text("Author: " + PluginConstants.AUTHOR, NamedTextColor.GRAY));
        source.sendMessage(Texts.muted("/bcd reload - reload configuration"));
        source.sendMessage(Texts.muted("/bcd status - authentication system status"));
        source.sendMessage(Texts.muted("/bcd debug <player> - live identity diagnostics"));
        source.sendMessage(Texts.muted("/bcd lookup <username> - stored identity information"));
        source.sendMessage(Texts.muted("/bcd check <username> - live Mojang profile classification"));
        source.sendMessage(Texts.muted("/bcd cache refresh <username> - invalidate and refresh profile cache"));
        source.sendMessage(Texts.muted("/bcd diagnostics <player> - full authentication diagnostics"));
        source.sendMessage(Texts.muted("/bcd premium add <username> (optional reservation)"));
        source.sendMessage(Texts.muted("/bcd premium remove <username>"));
        source.sendMessage(Texts.muted("/bcd premium list"));
        source.sendMessage(Texts.muted("/bcd premium refresh <username>"));
        source.sendMessage(Texts.muted("/bcd premium rename <oldUsername> <newUsername>"));
    }

    private void reload(CommandSource source) {
        if (!has(source, PluginConstants.PERMISSION_RELOAD)) {
            deny(source);
            return;
        }
        try {
            reloadAction.run();
            source.sendMessage(Texts.ok("Configuration reloaded."));
        } catch (RuntimeException ex) {
            source.sendMessage(Texts.error("Reload failed: " + ex.getMessage()));
        }
    }

    private void status(CommandSource source) {
        if (!has(source, PluginConstants.PERMISSION_ADMIN) && !has(source, PluginConstants.PERMISSION_DEBUG)) {
            deny(source);
            return;
        }
        PluginConfig config = services.config();
        source.sendMessage(Texts.info(PluginConstants.NAME + " v" + PluginConstants.VERSION));
        source.sendMessage(detail("Velocity version", proxy.getVersion().getVersion()));
        source.sendMessage(detail("Compiled against Velocity API", PluginConstants.TESTED_VELOCITY_API));
        source.sendMessage(detail("Database", services.database().isOpen()
                ? "SQLite OK (" + services.database().databaseFile().getFileName() + ")"
                : "closed"));
        source.sendMessage(detail("Cached Premium accounts", String.valueOf(services.identities().premiumCount())));
        source.sendMessage(detail("Identity records", String.valueOf(services.identities().identityCount())));
        source.sendMessage(detail("Automatic Premium detection", enabled(config.premiumDetection().automatic())));
        source.sendMessage(detail("Force online authentication", enabled(config.premiumDetection().forceOnlineAuthentication())));
        source.sendMessage(detail("Require authenticated session", enabled(config.premiumDetection().requireAuthenticatedSession())));
        source.sendMessage(detail("Verify Mojang UUID", enabled(config.premiumDetection().verifyMojangUuid())));
        source.sendMessage(detail("Never downgrade to cracked", enabled(config.premiumDetection().neverDowngradeToCracked())));
        source.sendMessage(detail("Cracked support", enabled(config.cracked().enabled())));
        source.sendMessage(detail("Require definitive profile-not-found", enabled(config.cracked().requireDefinitiveProfileNotFound())));
        source.sendMessage(detail("Preserve offline UUID", enabled(config.cracked().preserveOfflineUuid())));
        source.sendMessage(detail("LimboAuth adapter", services.limboAuth().attached()
                ? "attached (" + services.limboAuth().detectedVersion() + ")"
                : "inactive (" + services.limboAuth().detectedVersion() + ")"));
        source.sendMessage(detail("LimboAPI", services.limboAuth().limboApiVersion()));
        source.sendMessage(detail("Strict security / fail-closed", enabled(config.failClosed())));
        source.sendMessage(detail("Negative cache seconds", String.valueOf(config.negativeCacheSeconds())));
        source.sendMessage(detail("Debug", enabled(config.debug())));
    }

    private void debug(CommandSource source, String[] args) {
        if (!has(source, PluginConstants.PERMISSION_DEBUG)) {
            deny(source);
            return;
        }
        if (args.length < 2) {
            source.sendMessage(Texts.error("Usage: /bcd debug <player>"));
            return;
        }
        Optional<Player> player = proxy.getPlayer(args[1]);
        if (player.isEmpty()) {
            source.sendMessage(Texts.warn("Player is not online. Showing stored lookup instead."));
            sendReport(source, services.inspector().inspectStored(args[1]));
            return;
        }
        Player online = player.get();
        VerifiedSession session = services.connections().verifiedSession(online);
        IdentityInspector.DiagnosticReport report = session == null
                ? services.inspector().inspectStored(online.getUsername())
                : services.inspector().inspect(
                        online.getUsername(),
                        online.getUniqueId(),
                        session.accountType(),
                        session.authenticationResult(),
                        session.uuidSource(),
                        session.backendForwardingUuid()
                );
        source.sendMessage(Texts.info("Live connection diagnostics for " + online.getUsername()));
        source.sendMessage(detail("Online-mode session", String.valueOf(online.isOnlineMode())));
        if (session != null) {
            sendDiagnostics(source, services.inspector().inspectLive(
                    session,
                    online.isOnlineMode(),
                    limboStatus(online, session)
            ));
            return;
        }
        sendReport(source, report);
    }

    private void lookup(CommandSource source, String[] args) {
        if (!has(source, PluginConstants.PERMISSION_DEBUG) && !has(source, PluginConstants.PERMISSION_ADMIN)) {
            deny(source);
            return;
        }
        if (args.length < 2) {
            source.sendMessage(Texts.error("Usage: /bcd lookup <username>"));
            return;
        }
        sendReport(source, services.inspector().inspectStored(args[1]));
    }

    private void check(CommandSource source, String[] args) {
        if (!has(source, PluginConstants.PERMISSION_DEBUG) && !has(source, PluginConstants.PERMISSION_ADMIN)) {
            deny(source);
            return;
        }
        if (args.length < 2) {
            source.sendMessage(Texts.error("Usage: /bcd check <username>"));
            return;
        }
        String username = args[1];
        runAsync(source, () -> {
            var lookup = services.mojangApi().lookupByUsername(username);
            source.sendMessage(Texts.info("Profile check for " + username));
            source.sendMessage(detail("Mojang lookup status", lookup.status().name()));
            source.sendMessage(detail("HTTP status", String.valueOf(lookup.httpStatus())));
            if (lookup.profile().isPresent()) {
                var profile = lookup.profile().get();
                source.sendMessage(detail("Classification", "PREMIUM_CANDIDATE (session still required)"));
                source.sendMessage(detail("Canonical username", profile.canonicalUsername()));
                source.sendMessage(detail("Expected Mojang UUID", UuidNormalizer.toDashed(profile.uuid())));
            } else if (lookup.status() == net.blazemc.connectiondetector.auth.MojangLookupResult.Status.NOT_FOUND) {
                source.sendMessage(detail("Classification", "CRACKED_CONFIRMED (definitive profile-not-found)"));
            } else {
                source.sendMessage(detail("Classification", "MOJANG_API_UNAVAILABLE (fail-closed, not cracked)"));
                source.sendMessage(detail("Detail", lookup.detail()));
            }
            Optional<PremiumAccount> reserved = services.authentication().findReserved(username);
            source.sendMessage(detail(
                    "Local Premium cache",
                    reserved.map(account -> "yes " + UuidNormalizer.toDashed(account.mojangUuid()))
                            .orElse("none")
            ));
        });
    }

    private void cache(CommandSource source, String[] args) {
        if (!has(source, PluginConstants.PERMISSION_ADMIN) && !has(source, PluginConstants.PERMISSION_DEBUG)) {
            deny(source);
            return;
        }
        if (args.length < 3 || !args[1].equalsIgnoreCase("refresh")) {
            source.sendMessage(Texts.error("Usage: /bcd cache refresh <username>"));
            return;
        }
        String username = args[2];
        runAsync(source, () -> {
            if (services.mojangApi() != null) {
                services.mojangApi().invalidate(username);
            }
            if (services.mojangCache() != null) {
                services.mojangCache().invalidate(username);
            }
            var lookup = services.mojangApi().lookupByUsername(username);
            source.sendMessage(Texts.ok("Cache refreshed for " + username + ": " + lookup.status()));
            lookup.profile().ifPresent(profile -> source.sendMessage(detail(
                    "Profile",
                    profile.canonicalUsername() + " " + UuidNormalizer.toDashed(profile.uuid())
            )));
        });
    }

    private void diagnostics(CommandSource source, String[] args) {
        if (!has(source, PluginConstants.PERMISSION_DEBUG)) {
            deny(source);
            return;
        }
        if (args.length < 2) {
            source.sendMessage(Texts.error("Usage: /bcd diagnostics <player>"));
            return;
        }
        Optional<Player> player = proxy.getPlayer(args[1]);
        if (player.isEmpty()) {
            source.sendMessage(Texts.warn("Player is not online. Showing stored diagnostics."));
            sendDiagnostics(source, services.inspector().inspectStored(args[1]));
            return;
        }
        Player online = player.get();
        VerifiedSession session = services.connections().verifiedSession(online);
        IdentityInspector.DiagnosticReport report = session == null
                ? services.inspector().inspectStored(online.getUsername())
                : services.inspector().inspectLive(session, online.isOnlineMode(), limboStatus(online, session));
        source.sendMessage(Texts.info("Authentication diagnostics for " + online.getUsername()));
        sendDiagnostics(source, report);
    }

    private void sendDiagnostics(CommandSource source, IdentityInspector.DiagnosticReport report) {
        source.sendMessage(detail("Username", report.username()));
        source.sendMessage(detail("Account type", String.valueOf(report.accountType())));
        source.sendMessage(detail(
                "Expected Mojang UUID",
                report.expectedMojangUuid() == null ? "none" : UuidNormalizer.toDashed(report.expectedMojangUuid())
        ));
        source.sendMessage(detail(
                "Authenticated UUID",
                report.authenticatedUuid() == null ? "none" : UuidNormalizer.toDashed(report.authenticatedUuid())
        ));
        source.sendMessage(detail("UUID source", String.valueOf(report.uuidSource())));
        source.sendMessage(detail("Online authentication status", String.valueOf(report.onlineAuthentication())));
        source.sendMessage(detail("Session verification status", String.valueOf(report.sessionVerified())));
        source.sendMessage(detail("Premium profile status", report.premiumProfileStatus()));
        source.sendMessage(detail("Connection state", String.valueOf(report.connectionState())));
        source.sendMessage(detail("LimboAuth integration status", report.limboAuthStatus()));
        source.sendMessage(detail(
                "Backend forwarding UUID",
                report.backendForwardingIdentity() == null
                        ? "none"
                        : UuidNormalizer.toDashed(report.backendForwardingIdentity())
        ));
        source.sendMessage(detail("Final login decision", report.finalLoginDecision()));
        if (report.hasSeparateCrackedAndPremiumProfiles()) {
            source.sendMessage(Texts.warn(
                    "Separate historical Cracked and Premium profiles exist. They are not merged."
            ));
        }
    }

    private String limboStatus(Player player, VerifiedSession session) {
        if (services.limboAuth() == null || !services.limboAuth().attached()) {
            return "adapter inactive";
        }
        if (session != null && session.verifiedPremium() && services.limboAuth().shouldBypass(player)) {
            return "bypass Premium password login";
        }
        if (session != null && session.accountType() == net.blazemc.connectiondetector.model.AccountType.CRACKED) {
            return "LimboAuth registration/login flow";
        }
        return "attached (" + services.limboAuth().detectedVersion() + ")";
    }

    private void sendReport(CommandSource source, IdentityInspector.DiagnosticReport report) {
        source.sendMessage(detail("Username", report.username()));
        source.sendMessage(detail("Account type", String.valueOf(report.accountType())));
        source.sendMessage(detail("UUID", report.uuid() == null ? "none" : UuidNormalizer.toDashed(report.uuid())));
        source.sendMessage(detail("UUID source", String.valueOf(report.uuidSource())));
        source.sendMessage(detail("Authentication result", String.valueOf(report.authenticationResult())));
        source.sendMessage(detail("Premium registration", report.premiumRegistered() ? "RESERVED" : "not reserved"));
        if (report.premiumAccount() != null) {
            PremiumAccount premium = report.premiumAccount();
            source.sendMessage(detail("Registered Mojang UUID", UuidNormalizer.toDashed(premium.mojangUuid())));
            source.sendMessage(detail("Canonical username", premium.canonicalUsername()));
            source.sendMessage(detail(
                    "Last successful Premium auth",
                    premium.lastSuccessfulAuth() == null ? "never" : premium.lastSuccessfulAuth().toString()
            ));
        }
        source.sendMessage(detail(
                "Backend forwarding identity",
                report.backendForwardingIdentity() == null
                        ? "none"
                        : UuidNormalizer.toDashed(report.backendForwardingIdentity())
        ));
        if (report.identityRecords().isEmpty()) {
            source.sendMessage(Texts.muted("No stored identity records for this username."));
        } else {
            source.sendMessage(Texts.info("Stored identity records (Premium and Cracked stay separate):"));
            for (IdentityRecord record : report.identityRecords()) {
                source.sendMessage(Component.text(
                        "  - " + record.accountType()
                                + " " + UuidNormalizer.toDashed(record.uuid())
                                + " source=" + record.uuidSource()
                                + " user=" + record.lastKnownUsername(),
                        NamedTextColor.GRAY
                ));
            }
        }
        if (report.hasSeparateCrackedAndPremiumProfiles()) {
            source.sendMessage(Texts.warn(
                    "This username has separate Cracked and Premium profiles. Data is not merged."
            ));
        }
    }

    private void premium(CommandSource source, String[] args) {
        if (!has(source, PluginConstants.PERMISSION_PREMIUM_MANAGE)) {
            deny(source);
            return;
        }
        if (args.length < 2) {
            source.sendMessage(Texts.error("Usage: /bcd premium <add|remove|list|refresh|rename>"));
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                if (args.length < 3) {
                    source.sendMessage(Texts.error("Usage: /bcd premium add <username>"));
                    return;
                }
                runAsync(source, () -> handleAdd(source, args[2]));
            }
            case "remove" -> {
                if (args.length < 3) {
                    source.sendMessage(Texts.error("Usage: /bcd premium remove <username> [confirm]"));
                    return;
                }
                boolean confirm = args.length >= 4 && args[3].equalsIgnoreCase("confirm");
                handleRemove(source, args[2], confirm);
            }
            case "list" -> handleList(source);
            case "refresh" -> {
                if (args.length < 3) {
                    source.sendMessage(Texts.error("Usage: /bcd premium refresh <username>"));
                    return;
                }
                runAsync(source, () -> handleRefresh(source, args[2]));
            }
            case "rename" -> {
                if (args.length < 4) {
                    source.sendMessage(Texts.error("Usage: /bcd premium rename <oldUsername> <newUsername>"));
                    return;
                }
                runAsync(source, () -> handleRename(source, args[2], args[3]));
            }
            default -> source.sendMessage(Texts.error("Unknown premium subcommand."));
        }
    }

    private void handleAdd(CommandSource source, String username) {
        PremiumRegistrationService.Result result = services.registration().add(username);
        switch (result.outcome()) {
            case REGISTERED -> {
                PremiumAccount account = result.account();
                source.sendMessage(Texts.ok("Reserved Premium account " + account.canonicalUsername()
                        + " with Mojang UUID " + UuidNormalizer.toDashed(account.mojangUuid()) + "."));
                source.sendMessage(Texts.muted("Existing Cracked profiles for this name were not modified."));
            }
            case DUPLICATE_USERNAME -> source.sendMessage(Texts.error("That username is already reserved."));
            case DUPLICATE_UUID -> source.sendMessage(Texts.error(
                    "That Mojang UUID is already reserved as " + result.account().canonicalUsername() + "."
            ));
            case NOT_PREMIUM -> source.sendMessage(Texts.error(
                    "Mojang has no Premium profile for " + username + ". The name was not reserved."
            ));
            case API_UNAVAILABLE -> source.sendMessage(Texts.error(
                    "Mojang API is unavailable (" + result.detail()
                            + "). Fail-closed: the username was NOT treated as cracked and was not reserved."
            ));
            case INVALID_USERNAME -> source.sendMessage(Texts.error("Invalid Minecraft username."));
            default -> source.sendMessage(Texts.error("Unable to reserve Premium account: " + result.detail()));
        }
    }

    private void handleRemove(CommandSource source, String username, boolean confirmFlag) {
        String key = sourceKey(source) + ":remove:" + username.toLowerCase(Locale.ROOT);
        if (!confirmFlag) {
            confirmations.arm(key);
            source.sendMessage(Texts.warn(
                    "This only removes the Premium reservation. Identity records are never deleted."
            ));
            source.sendMessage(Texts.warn(
                    "Re-run /bcd premium remove " + username + " confirm within 30 seconds to confirm."
            ));
            return;
        }
        if (!confirmations.consume(key)) {
            source.sendMessage(Texts.error("Confirmation expired. Run /bcd premium remove " + username + " again."));
            return;
        }
        PremiumRegistrationService.Result result = services.registration().remove(username);
        if (result.outcome() == PremiumRegistrationService.Outcome.REMOVED) {
            source.sendMessage(Texts.ok("Removed Premium reservation for " + result.account().canonicalUsername() + "."));
            source.sendMessage(Texts.muted("Stored identity records and historical player data were left untouched."));
        } else {
            source.sendMessage(Texts.error("That username is not reserved."));
        }
    }

    private void handleList(CommandSource source) {
        List<PremiumAccount> accounts = services.registration().list();
        if (accounts.isEmpty()) {
            source.sendMessage(Texts.warn("No Premium accounts are reserved."));
            return;
        }
        source.sendMessage(Texts.info("Reserved Premium accounts (" + accounts.size() + "):"));
        for (PremiumAccount account : accounts) {
            source.sendMessage(Component.text(
                    "  - " + account.canonicalUsername()
                            + " " + UuidNormalizer.toDashed(account.mojangUuid()),
                    NamedTextColor.GRAY
            ));
        }
    }

    private void handleRefresh(CommandSource source, String username) {
        PremiumRegistrationService.Result result = services.registration().refresh(username);
        switch (result.outcome()) {
            case REFRESHED -> source.sendMessage(Texts.ok(
                    "Refreshed " + result.account().canonicalUsername()
                            + " / " + UuidNormalizer.toDashed(result.account().mojangUuid()) + "."
            ));
            case API_UNAVAILABLE -> source.sendMessage(Texts.warn(
                    "Mojang API unavailable (" + result.detail()
                            + "). Cached Premium identity remains in effect."
            ));
            case NOT_REGISTERED -> source.sendMessage(Texts.error("That username is not reserved."));
            case UUID_MISMATCH -> source.sendMessage(Texts.error(
                    "Mojang returned a different UUID. Cached identity was not overwritten."
            ));
            default -> source.sendMessage(Texts.error("Refresh failed: " + result.detail()));
        }
    }

    private void handleRename(CommandSource source, String oldUsername, String newUsername) {
        PremiumRegistrationService.Result result = services.registration().rename(oldUsername, newUsername);
        switch (result.outcome()) {
            case RENAMED -> source.sendMessage(Texts.ok(
                    "Updated reserved username to " + result.account().canonicalUsername()
                            + ". Mojang UUID " + UuidNormalizer.toDashed(result.account().mojangUuid()) + " is unchanged."
            ));
            case NOT_REGISTERED -> source.sendMessage(Texts.error("The original username is not reserved."));
            case TARGET_USERNAME_TAKEN -> source.sendMessage(Texts.error("The new username is already reserved."));
            case UUID_MISMATCH -> source.sendMessage(Texts.error(
                    "The new username belongs to a different Mojang account. UUID identity was preserved."
            ));
            case API_UNAVAILABLE -> source.sendMessage(Texts.error(
                    "Mojang API unavailable (" + result.detail() + "). Rename was not applied."
            ));
            default -> source.sendMessage(Texts.error("Rename failed: " + result.detail()));
        }
    }

    private void runAsync(CommandSource source, Runnable task) {
        CompletableFuture.runAsync(task).exceptionally(error -> {
            source.sendMessage(Texts.error("Command failed: " + error.getMessage()));
            return null;
        });
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length == 0) {
            return List.of("reload", "status", "debug", "lookup", "check", "cache", "diagnostics", "premium");
        }
        if (args.length == 1) {
            return prefix(args[0], "reload", "status", "debug", "lookup", "check", "cache", "diagnostics", "premium");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("premium")) {
            return prefix(args[1], "add", "remove", "list", "refresh", "rename");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("cache")) {
            return prefix(args[1], "refresh");
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("debug")
                || args[0].equalsIgnoreCase("lookup")
                || args[0].equalsIgnoreCase("check")
                || args[0].equalsIgnoreCase("diagnostics"))) {
            return proxy.getAllPlayers().stream()
                    .map(Player::getUsername)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("cache") && args[1].equalsIgnoreCase("refresh")) {
            return proxy.getAllPlayers().stream()
                    .map(Player::getUsername)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[2].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("premium") && args[1].equalsIgnoreCase("remove")) {
            return List.of("confirm");
        }
        return List.of();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return true;
    }

    private static List<String> prefix(String input, String... options) {
        String needle = input.toLowerCase(Locale.ROOT);
        return Stream.of(options).filter(option -> option.startsWith(needle)).toList();
    }

    private static boolean has(CommandSource source, String permission) {
        return source.hasPermission(PluginConstants.PERMISSION_ADMIN) || source.hasPermission(permission);
    }

    private static void deny(CommandSource source) {
        source.sendMessage(Texts.error("You do not have permission to use this command."));
    }

    private static Component detail(String key, String value) {
        return Component.text("  " + key + ": ", NamedTextColor.GRAY)
                .append(Component.text(value, NamedTextColor.WHITE));
    }

    private static String enabled(boolean value) {
        return value ? "enabled" : "disabled";
    }

    private static String sourceKey(CommandSource source) {
        if (source instanceof Player player) {
            return player.getUniqueId().toString();
        }
        return "console";
    }

}
