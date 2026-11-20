package net.blazemc.connectiondetector.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class PremiumAccount {

    private final String canonicalUsername;
    private final String lowercaseUsername;
    private final UUID mojangUuid;
    private final Instant registeredAt;
    private final Instant lastSuccessfulAuth;
    private final String lastKnownUsername;
    private final Instant lastRefreshedAt;

    public PremiumAccount(
            String canonicalUsername,
            UUID mojangUuid,
            Instant registeredAt,
            Instant lastSuccessfulAuth,
            String lastKnownUsername,
            Instant lastRefreshedAt
    ) {
        this.canonicalUsername = Objects.requireNonNull(canonicalUsername, "canonicalUsername");
        this.lowercaseUsername = canonicalUsername.toLowerCase();
        this.mojangUuid = Objects.requireNonNull(mojangUuid, "mojangUuid");
        this.registeredAt = Objects.requireNonNull(registeredAt, "registeredAt");
        this.lastSuccessfulAuth = lastSuccessfulAuth;
        this.lastKnownUsername = lastKnownUsername == null ? canonicalUsername : lastKnownUsername;
        this.lastRefreshedAt = lastRefreshedAt;
    }

    public String canonicalUsername() {
        return canonicalUsername;
    }

    public String lowercaseUsername() {
        return lowercaseUsername;
    }

    public UUID mojangUuid() {
        return mojangUuid;
    }

    public Instant registeredAt() {
        return registeredAt;
    }

    public Instant lastSuccessfulAuth() {
        return lastSuccessfulAuth;
    }

    public String lastKnownUsername() {
        return lastKnownUsername;
    }

    public Instant lastRefreshedAt() {
        return lastRefreshedAt;
    }

    public PremiumAccount withSuccessfulAuth(String username, Instant when) {
        return new PremiumAccount(canonicalUsername, mojangUuid, registeredAt, when, username, lastRefreshedAt);
    }

    public PremiumAccount withCanonicalUsername(String newUsername, Instant refreshedAt) {
        return new PremiumAccount(newUsername, mojangUuid, registeredAt, lastSuccessfulAuth, newUsername, refreshedAt);
    }
}
