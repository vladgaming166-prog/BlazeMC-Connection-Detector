package net.blazemc.connectiondetector.auth;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.blazemc.connectiondetector.config.PluginConfig;
import net.blazemc.connectiondetector.model.PremiumAccount;

public final class PremiumRegistrationService {

    public enum Outcome {
        REGISTERED,
        REFRESHED,
        RENAMED,
        REMOVED,
        DUPLICATE_USERNAME,
        DUPLICATE_UUID,
        NOT_PREMIUM,
        API_UNAVAILABLE,
        NOT_REGISTERED,
        INVALID_USERNAME,
        UUID_MISMATCH,
        TARGET_USERNAME_TAKEN
    }

    public record Result(Outcome outcome, PremiumAccount account, String detail) {
        public boolean success() {
            return outcome == Outcome.REGISTERED
                    || outcome == Outcome.REFRESHED
                    || outcome == Outcome.RENAMED
                    || outcome == Outcome.REMOVED;
        }
    }

    private final PremiumAccountStore store;
    private final MojangApi mojangApi;
    private final PluginConfig config;

    public PremiumRegistrationService(PremiumAccountStore store, MojangApi mojangApi, PluginConfig config) {
        this.store = store;
        this.mojangApi = mojangApi;
        this.config = config;
    }

    public Result add(String username) {
        if (!UsernameValidator.isValid(username)) {
            return new Result(Outcome.INVALID_USERNAME, null, "invalid username");
        }
        Optional<PremiumAccount> existingName = store.findByUsername(username);
        if (existingName.isPresent()) {
            return new Result(Outcome.DUPLICATE_USERNAME, existingName.get(), "username already reserved");
        }

        MojangLookupResult lookup = mojangApi.lookupByUsername(username);
        if (lookup.status() == MojangLookupResult.Status.NOT_FOUND) {
            return new Result(Outcome.NOT_PREMIUM, null, "Mojang has no profile for this username");
        }
        if (lookup.isUnavailable() || lookup.profile().isEmpty()) {
            return unavailable(lookup);
        }

        MojangProfile profile = lookup.profile().get();
        Optional<PremiumAccount> existingUuid = store.findByUuid(profile.uuid());
        if (existingUuid.isPresent()) {
            return new Result(
                    Outcome.DUPLICATE_UUID,
                    existingUuid.get(),
                    "UUID already reserved as " + existingUuid.get().canonicalUsername()
            );
        }

        Instant now = Instant.now();
        PremiumAccount account = new PremiumAccount(
                profile.canonicalUsername(),
                profile.uuid(),
                now,
                null,
                profile.canonicalUsername(),
                now
        );
        store.insert(account);
        return new Result(Outcome.REGISTERED, account, "reserved");
    }

    public Result refresh(String username) {
        Optional<PremiumAccount> existing = store.findByUsername(username);
        if (existing.isEmpty()) {
            return new Result(Outcome.NOT_REGISTERED, null, "not reserved");
        }

        MojangLookupResult lookup = mojangApi.lookupByUsername(username);
        if (lookup.isUnavailable()) {
            return new Result(Outcome.API_UNAVAILABLE, existing.get(), lookup.detail());
        }
        if (lookup.status() == MojangLookupResult.Status.NOT_FOUND) {
            lookup = mojangApi.lookupByUsername(existing.get().canonicalUsername());
        }
        if (lookup.status() == MojangLookupResult.Status.NOT_FOUND) {
            return new Result(Outcome.NOT_PREMIUM, existing.get(), "Mojang profile no longer found; cached identity kept");
        }
        if (lookup.profile().isEmpty()) {
            return unavailable(lookup);
        }

        MojangProfile profile = lookup.profile().get();
        if (!profile.uuid().equals(existing.get().mojangUuid())) {
            return new Result(Outcome.UUID_MISMATCH, existing.get(), "Mojang UUID does not match the stored Premium UUID");
        }

        PremiumAccount updated = existing.get().withCanonicalUsername(profile.canonicalUsername(), Instant.now());
        store.update(updated);
        return new Result(Outcome.REFRESHED, updated, "refreshed");
    }

    public Result rename(String oldUsername, String newUsername) {
        if (!UsernameValidator.isValid(oldUsername) || !UsernameValidator.isValid(newUsername)) {
            return new Result(Outcome.INVALID_USERNAME, null, "invalid username");
        }
        Optional<PremiumAccount> existing = store.findByUsername(oldUsername);
        if (existing.isEmpty()) {
            return new Result(Outcome.NOT_REGISTERED, null, "not reserved");
        }
        Optional<PremiumAccount> taken = store.findByUsername(newUsername);
        if (taken.isPresent() && !taken.get().mojangUuid().equals(existing.get().mojangUuid())) {
            return new Result(Outcome.TARGET_USERNAME_TAKEN, taken.get(), "target username is already reserved");
        }

        MojangLookupResult lookup = mojangApi.lookupByUsername(newUsername);
        if (lookup.status() == MojangLookupResult.Status.NOT_FOUND) {
            return new Result(Outcome.NOT_PREMIUM, existing.get(), "new username is not a Mojang profile");
        }
        if (lookup.isUnavailable() || lookup.profile().isEmpty()) {
            return unavailable(lookup);
        }

        MojangProfile profile = lookup.profile().get();
        if (!profile.uuid().equals(existing.get().mojangUuid())) {
            return new Result(Outcome.UUID_MISMATCH, existing.get(), "new username belongs to a different Mojang UUID");
        }

        PremiumAccount updated = existing.get().withCanonicalUsername(profile.canonicalUsername(), Instant.now());
        store.update(updated);
        return new Result(Outcome.RENAMED, updated, "renamed");
    }

    public Result remove(String username) {
        Optional<PremiumAccount> existing = store.findByUsername(username);
        if (existing.isEmpty()) {
            return new Result(Outcome.NOT_REGISTERED, null, "not reserved");
        }
        store.deleteByUsername(username);
        return new Result(Outcome.REMOVED, existing.get(), "removed reservation only");
    }

    public List<PremiumAccount> list() {
        return store.listAll();
    }

    public void recordSuccessfulAuth(PremiumAccount account, String username) {
        store.update(account.withSuccessfulAuth(username, Instant.now()));
    }

    public void recordSuccessfulAuth(UUID mojangUuid, String username) {
        store.findByUuid(mojangUuid).ifPresent(account -> recordSuccessfulAuth(account, username));
    }

    private Result unavailable(MojangLookupResult lookup) {
        String detail = lookup.detail() + " (HTTP " + lookup.httpStatus() + ")";
        if (config.mojang().failClosedOnApiError()) {
            return new Result(Outcome.API_UNAVAILABLE, null, detail);
        }
        return new Result(Outcome.API_UNAVAILABLE, null, detail);
    }
}
