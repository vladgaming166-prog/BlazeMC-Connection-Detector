package net.blazemc.connectiondetector.storage;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.blazemc.connectiondetector.auth.PremiumAccountStore;
import net.blazemc.connectiondetector.auth.UsernameValidator;
import net.blazemc.connectiondetector.auth.UuidNormalizer;
import net.blazemc.connectiondetector.model.PremiumAccount;

public final class SqlitePremiumAccountStore implements PremiumAccountStore {

    private final DatabaseManager database;

    public SqlitePremiumAccountStore(DatabaseManager database) {
        this.database = database;
    }

    @Override
    public Optional<PremiumAccount> findByUsername(String username) {
        try {
            return database.withLock(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT * FROM premium_accounts WHERE lowercase_username = ? LIMIT 1"
                )) {
                    statement.setString(1, UsernameValidator.normalizeKey(username));
                    try (ResultSet rs = statement.executeQuery()) {
                        if (rs.next()) {
                            return Optional.of(map(rs));
                        }
                        return Optional.empty();
                    }
                }
            });
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to look up premium account by username", ex);
        }
    }

    @Override
    public Optional<PremiumAccount> findByUuid(UUID mojangUuid) {
        try {
            return database.withLock(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT * FROM premium_accounts WHERE mojang_uuid = ? LIMIT 1"
                )) {
                    statement.setString(1, UuidNormalizer.toDashed(mojangUuid));
                    try (ResultSet rs = statement.executeQuery()) {
                        if (rs.next()) {
                            return Optional.of(map(rs));
                        }
                        return Optional.empty();
                    }
                }
            });
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to look up premium account by UUID", ex);
        }
    }

    @Override
    public List<PremiumAccount> listAll() {
        try {
            return database.withLock(connection -> {
                List<PremiumAccount> accounts = new ArrayList<>();
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT * FROM premium_accounts ORDER BY canonical_username COLLATE NOCASE"
                );
                     ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        accounts.add(map(rs));
                    }
                }
                return accounts;
            });
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to list premium accounts", ex);
        }
    }

    @Override
    public void insert(PremiumAccount account) {
        try {
            database.runInTransaction(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        """
                        INSERT INTO premium_accounts (
                            canonical_username, lowercase_username, mojang_uuid,
                            registered_at, last_successful_auth, last_known_username, last_refreshed_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?)
                        """
                )) {
                    bind(statement, account);
                    statement.executeUpdate();
                }
            });
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to insert premium account", ex);
        }
    }

    @Override
    public void update(PremiumAccount account) {
        try {
            database.runInTransaction(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        """
                        UPDATE premium_accounts SET
                            canonical_username = ?,
                            lowercase_username = ?,
                            last_successful_auth = ?,
                            last_known_username = ?,
                            last_refreshed_at = ?
                        WHERE mojang_uuid = ?
                        """
                )) {
                    statement.setString(1, account.canonicalUsername());
                    statement.setString(2, account.lowercaseUsername());
                    if (account.lastSuccessfulAuth() == null) {
                        statement.setObject(3, null);
                    } else {
                        statement.setLong(3, account.lastSuccessfulAuth().toEpochMilli());
                    }
                    statement.setString(4, account.lastKnownUsername());
                    if (account.lastRefreshedAt() == null) {
                        statement.setObject(5, null);
                    } else {
                        statement.setLong(5, account.lastRefreshedAt().toEpochMilli());
                    }
                    statement.setString(6, UuidNormalizer.toDashed(account.mojangUuid()));
                    statement.executeUpdate();
                }
            });
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to update premium account", ex);
        }
    }

    @Override
    public boolean deleteByUsername(String username) {
        try {
            return database.callInTransaction(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM premium_accounts WHERE lowercase_username = ?"
                )) {
                    statement.setString(1, UsernameValidator.normalizeKey(username));
                    return statement.executeUpdate() > 0;
                }
            });
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to delete premium reservation", ex);
        }
    }

    private static void bind(PreparedStatement statement, PremiumAccount account) throws SQLException {
        statement.setString(1, account.canonicalUsername());
        statement.setString(2, account.lowercaseUsername());
        statement.setString(3, UuidNormalizer.toDashed(account.mojangUuid()));
        statement.setLong(4, account.registeredAt().toEpochMilli());
        if (account.lastSuccessfulAuth() == null) {
            statement.setObject(5, null);
        } else {
            statement.setLong(5, account.lastSuccessfulAuth().toEpochMilli());
        }
        statement.setString(6, account.lastKnownUsername());
        if (account.lastRefreshedAt() == null) {
            statement.setObject(7, null);
        } else {
            statement.setLong(7, account.lastRefreshedAt().toEpochMilli());
        }
    }

    private static PremiumAccount map(ResultSet rs) throws SQLException {
        return new PremiumAccount(
                rs.getString("canonical_username"),
                UuidNormalizer.fromMojang(rs.getString("mojang_uuid")),
                Instant.ofEpochMilli(rs.getLong("registered_at")),
                nullableInstant(rs, "last_successful_auth"),
                rs.getString("last_known_username"),
                nullableInstant(rs, "last_refreshed_at")
        );
    }

    private static Instant nullableInstant(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        if (rs.wasNull()) {
            return null;
        }
        return Instant.ofEpochMilli(value);
    }
}
