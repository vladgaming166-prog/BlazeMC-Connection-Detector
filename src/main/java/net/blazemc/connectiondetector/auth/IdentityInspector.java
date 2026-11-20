package net.blazemc.connectiondetector.auth;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.blazemc.connectiondetector.model.AccountType;
import net.blazemc.connectiondetector.model.AuthenticationResult;
import net.blazemc.connectiondetector.model.ConnectionHistoryEntry;
import net.blazemc.connectiondetector.model.ConnectionState;
import net.blazemc.connectiondetector.model.IdentityRecord;
import net.blazemc.connectiondetector.model.PremiumAccount;
import net.blazemc.connectiondetector.model.UuidSource;
import net.blazemc.connectiondetector.storage.IdentityRepository;

public final class IdentityInspector {

    private final PremiumAccountStore premiumStore;
    private final IdentityRepository identities;

    public IdentityInspector(PremiumAccountStore premiumStore, IdentityRepository identities) {
        this.premiumStore = premiumStore;
        this.identities = identities;
    }

    public DiagnosticReport inspect(
            String username,
            UUID uuid,
            AccountType accountType,
            AuthenticationResult authenticationResult,
            UuidSource uuidSource,
            UUID backendForwardingUuid
    ) {
        return inspect(username, uuid, accountType, authenticationResult, uuidSource, backendForwardingUuid,
                null, false, false, ConnectionState.UNKNOWN, "unknown", "stored");
    }

    public DiagnosticReport inspectLive(VerifiedSession session, boolean onlineMode, String limboAuthStatus) {
        return inspect(
                session.username(),
                session.uuid(),
                session.accountType(),
                session.authenticationResult(),
                session.uuidSource(),
                session.backendForwardingUuid(),
                session.expectedMojangUuid(),
                onlineMode,
                session.sessionVerified(),
                session.connectionState(),
                limboAuthStatus,
                session.verifiedPremium() ? "allow premium" : (session.accountType() == AccountType.CRACKED
                        ? "allow cracked" : String.valueOf(session.authenticationResult()))
        );
    }

    public DiagnosticReport inspect(
            String username,
            UUID uuid,
            AccountType accountType,
            AuthenticationResult authenticationResult,
            UuidSource uuidSource,
            UUID backendForwardingUuid,
            UUID expectedMojangUuid,
            boolean onlineAuthentication,
            boolean sessionVerified,
            ConnectionState connectionState,
            String limboAuthStatus,
            String finalLoginDecision
    ) {
        Optional<PremiumAccount> premium = premiumStore.findByUsername(username);
        if (premium.isEmpty() && uuid != null) {
            premium = premiumStore.findByUuid(uuid);
        }
        List<IdentityRecord> records = identities.findByUsername(username);
        List<ConnectionHistoryEntry> history = identities.recentHistory(username, 5);
        UUID expected = expectedMojangUuid != null
                ? expectedMojangUuid
                : premium.map(PremiumAccount::mojangUuid).orElse(null);
        String premiumStatus = premium.isPresent()
                ? "cached Premium UUID " + premium.get().mojangUuid()
                : "no cached Premium profile";
        return new DiagnosticReport(
                username,
                accountType,
                uuid,
                uuidSource,
                authenticationResult,
                premium.isPresent(),
                premium.orElse(null),
                backendForwardingUuid,
                records,
                history,
                expected,
                uuid,
                onlineAuthentication,
                sessionVerified,
                connectionState,
                premiumStatus,
                limboAuthStatus,
                finalLoginDecision
        );
    }

    public DiagnosticReport inspectStored(String username) {
        Optional<PremiumAccount> premium = premiumStore.findByUsername(username);
        List<IdentityRecord> records = identities.findByUsername(username);
        List<ConnectionHistoryEntry> history = identities.recentHistory(username, 5);
        AccountType type = premium.isPresent() ? AccountType.PREMIUM : AccountType.UNKNOWN;
        UUID uuid = premium.map(PremiumAccount::mojangUuid).orElse(null);
        UuidSource source = premium.isPresent() ? UuidSource.STORED : UuidSource.UNKNOWN;
        if (premium.isEmpty() && !records.isEmpty()) {
            IdentityRecord first = records.get(0);
            type = records.size() == 1 ? first.accountType() : AccountType.UNKNOWN;
            uuid = first.uuid();
            source = first.uuidSource();
        }
        String premiumStatus = premium.isPresent()
                ? "cached Premium UUID " + premium.get().mojangUuid()
                : "no cached Premium profile";
        return new DiagnosticReport(
                username,
                type,
                uuid,
                source,
                premium.isPresent() ? AuthenticationResult.PREMIUM_VERIFIED : AuthenticationResult.CRACKED_CONFIRMED,
                premium.isPresent(),
                premium.orElse(null),
                uuid,
                records,
                history,
                premium.map(PremiumAccount::mojangUuid).orElse(null),
                uuid,
                false,
                false,
                premium.isPresent() ? ConnectionState.PREMIUM_CANDIDATE : ConnectionState.UNKNOWN,
                premiumStatus,
                "offline / not connected",
                "stored lookup only"
        );
    }

    public record DiagnosticReport(
            String username,
            AccountType accountType,
            UUID uuid,
            UuidSource uuidSource,
            AuthenticationResult authenticationResult,
            boolean premiumRegistered,
            PremiumAccount premiumAccount,
            UUID backendForwardingIdentity,
            List<IdentityRecord> identityRecords,
            List<ConnectionHistoryEntry> recentHistory,
            UUID expectedMojangUuid,
            UUID authenticatedUuid,
            boolean onlineAuthentication,
            boolean sessionVerified,
            ConnectionState connectionState,
            String premiumProfileStatus,
            String limboAuthStatus,
            String finalLoginDecision
    ) {
        public boolean hasSeparateCrackedAndPremiumProfiles() {
            boolean cracked = identityRecords.stream().anyMatch(record -> record.accountType() == AccountType.CRACKED);
            boolean premium = identityRecords.stream().anyMatch(record -> record.accountType() == AccountType.PREMIUM)
                    || premiumRegistered;
            return cracked && premium;
        }
    }
}
