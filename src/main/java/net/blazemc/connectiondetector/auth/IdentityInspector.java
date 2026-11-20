package net.blazemc.connectiondetector.auth;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.blazemc.connectiondetector.model.AccountType;
import net.blazemc.connectiondetector.model.AuthenticationResult;
import net.blazemc.connectiondetector.model.ConnectionHistoryEntry;
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
        Optional<PremiumAccount> premium = premiumStore.findByUsername(username);
        if (premium.isEmpty() && uuid != null) {
            premium = premiumStore.findByUuid(uuid);
        }
        List<IdentityRecord> records = identities.findByUsername(username);
        List<ConnectionHistoryEntry> history = identities.recentHistory(username, 5);
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
                history
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
        return new DiagnosticReport(
                username,
                type,
                uuid,
                source,
                AuthenticationResult.CRACKED_ALLOWED,
                premium.isPresent(),
                premium.orElse(null),
                uuid,
                records,
                history
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
            List<ConnectionHistoryEntry> recentHistory
    ) {
        public boolean hasSeparateCrackedAndPremiumProfiles() {
            boolean cracked = identityRecords.stream().anyMatch(record -> record.accountType() == AccountType.CRACKED);
            boolean premium = identityRecords.stream().anyMatch(record -> record.accountType() == AccountType.PREMIUM)
                    || premiumRegistered;
            return cracked && premium;
        }
    }
}
