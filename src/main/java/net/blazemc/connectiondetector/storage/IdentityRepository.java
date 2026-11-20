package net.blazemc.connectiondetector.storage;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.blazemc.connectiondetector.auth.UsernameValidator;
import net.blazemc.connectiondetector.auth.UuidNormalizer;
import net.blazemc.connectiondetector.auth.VerifiedSession;
import net.blazemc.connectiondetector.model.AccountType;
import net.blazemc.connectiondetector.model.AuthenticationResult;
import net.blazemc.connectiondetector.model.ConnectionHistoryEntry;
import net.blazemc.connectiondetector.model.IdentityRecord;
import net.blazemc.connectiondetector.model.UuidSource;

public final class IdentityRepository {

    private final DatabaseManager database;

    public IdentityRepository(DatabaseManager database) {
        this.database = database;
    }

    public void recordSuccessfulIdentity(VerifiedSession session) {
        if (session.uuid() == null) {
            return;
        }
        Instant now = Instant.now();
        try {
            database.runInTransaction(connection -> {
                Optional<IdentityRecord> existing = findByUuidAndType(
                        connection,
                        session.uuid(),
                        session.accountType()
                );
                if (existing.isPresent()) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            """
                            UPDATE identity_records
                            SET last_known_username = ?, lowercase_username = ?, last_seen = ?, uuid_source = ?
                            WHERE uuid = ? AND account_type = ?
                            """
                    )) {
                        statement.setString(1, session.username());
                        statement.setString(2, UsernameValidator.normalizeKey(session.username()));
                        statement.setLong(3, now.toEpochMilli());
                        statement.setString(4, session.uuidSource().name());
                        statement.setString(5, UuidNormalizer.toDashed(session.uuid()));
                        statement.setString(6, session.accountType().name());
                        statement.executeUpdate();
                    }
                } else {
                    try (PreparedStatement statement = connection.prepareStatement(
                            """
                            INSERT INTO identity_records (
                                uuid, uuid_source, account_type, last_known_username,
                                lowercase_username, first_seen, last_seen
                            ) VALUES (?, ?, ?, ?, ?, ?, ?)
                            """
                    )) {
                        statement.setString(1, UuidNormalizer.toDashed(session.uuid()));
                        statement.setString(2, session.uuidSource().name());
                        statement.setString(3, session.accountType().name());
                        statement.setString(4, session.username());
                        statement.setString(5, UsernameValidator.normalizeKey(session.username()));
                        statement.setLong(6, now.toEpochMilli());
                        statement.setLong(7, now.toEpochMilli());
                        statement.executeUpdate();
                    }
                }
            });
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to persist identity record", ex);
        }
    }

    public void recordHistory(VerifiedSession session, String notes) {
        recordHistory(new ConnectionHistoryEntry(
                session.username(),
                session.uuid(),
                session.accountType(),
                session.authenticationResult(),
                session.uuidSource(),
                session.premiumRegistered(),
                session.backendForwardingUuid(),
                Instant.now(),
                notes
        ));
    }

    public void recordHistory(ConnectionHistoryEntry entry) {
        try {
            database.runInTransaction(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        """
                        INSERT INTO connection_history (
                            username, uuid, account_type, authentication_result, uuid_source,
                            premium_registered, backend_forwarding_uuid, connected_at, notes
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """
                )) {
                    statement.setString(1, entry.username());
                    statement.setString(2, entry.uuid() == null ? null : UuidNormalizer.toDashed(entry.uuid()));
                    statement.setString(3, entry.accountType().name());
                    statement.setString(4, entry.authenticationResult().name());
                    statement.setString(5, entry.uuidSource() == null ? null : entry.uuidSource().name());
                    statement.setInt(6, entry.premiumRegistered() ? 1 : 0);
                    statement.setString(
                            7,
                            entry.backendForwardingUuid() == null
                                    ? null
                                    : UuidNormalizer.toDashed(entry.backendForwardingUuid())
                    );
                    statement.setLong(8, entry.connectedAt().toEpochMilli());
                    statement.setString(9, entry.notes());
                    statement.executeUpdate();
                }
            });
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to persist connection history", ex);
        }
    }

    public List<IdentityRecord> findByUsername(String username) {
        try {
            return database.withLock(connection -> {
                List<IdentityRecord> records = new ArrayList<>();
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT * FROM identity_records WHERE lowercase_username = ? ORDER BY account_type"
                )) {
                    statement.setString(1, UsernameValidator.normalizeKey(username));
                    try (ResultSet rs = statement.executeQuery()) {
                        while (rs.next()) {
                            records.add(mapIdentity(rs));
                        }
                    }
                }
                return records;
            });
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to look up identity records", ex);
        }
    }

    public Optional<IdentityRecord> findByUuid(UUID uuid) {
        try {
            return database.withLock(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT * FROM identity_records WHERE uuid = ? LIMIT 1"
                )) {
                    statement.setString(1, UuidNormalizer.toDashed(uuid));
                    try (ResultSet rs = statement.executeQuery()) {
                        if (rs.next()) {
                            return Optional.of(mapIdentity(rs));
                        }
                        return Optional.empty();
                    }
                }
            });
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to look up identity by UUID", ex);
        }
    }

    public List<ConnectionHistoryEntry> recentHistory(String username, int limit) {
        try {
            return database.withLock(connection -> {
                List<ConnectionHistoryEntry> entries = new ArrayList<>();
                try (PreparedStatement statement = connection.prepareStatement(
                        """
                        SELECT * FROM connection_history
                        WHERE lower(username) = ?
                        ORDER BY connected_at DESC
                        LIMIT ?
                        """
                )) {
                    statement.setString(1, UsernameValidator.normalizeKey(username));
                    statement.setInt(2, limit);
                    try (ResultSet rs = statement.executeQuery()) {
                        while (rs.next()) {
                            entries.add(mapHistory(rs));
                        }
                    }
                }
                return entries;
            });
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to look up connection history", ex);
        }
    }

    public long premiumCount() {
        try {
            return database.withLock(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM premium_accounts"
                );
                     ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : 0L;
                }
            });
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to count premium accounts", ex);
        }
    }

    public long identityCount() {
        try {
            return database.withLock(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM identity_records"
                );
                     ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : 0L;
                }
            });
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to count identity records", ex);
        }
    }

    private Optional<IdentityRecord> findByUuidAndType(
            java.sql.Connection connection,
            UUID uuid,
            AccountType type
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM identity_records WHERE uuid = ? AND account_type = ? LIMIT 1"
        )) {
            statement.setString(1, UuidNormalizer.toDashed(uuid));
            statement.setString(2, type.name());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapIdentity(rs));
                }
                return Optional.empty();
            }
        }
    }

    private static IdentityRecord mapIdentity(ResultSet rs) throws SQLException {
        return new IdentityRecord(
                UuidNormalizer.fromMojang(rs.getString("uuid")),
                UuidSource.valueOf(rs.getString("uuid_source")),
                AccountType.valueOf(rs.getString("account_type")),
                rs.getString("last_known_username"),
                Instant.ofEpochMilli(rs.getLong("first_seen")),
                Instant.ofEpochMilli(rs.getLong("last_seen"))
        );
    }

    private static ConnectionHistoryEntry mapHistory(ResultSet rs) throws SQLException {
        return new ConnectionHistoryEntry(
                rs.getString("username"),
                parseUuid(rs.getString("uuid")),
                AccountType.valueOf(rs.getString("account_type")),
                AuthenticationResult.valueOf(rs.getString("authentication_result")),
                parseSource(rs.getString("uuid_source")),
                rs.getInt("premium_registered") == 1,
                parseUuid(rs.getString("backend_forwarding_uuid")),
                Instant.ofEpochMilli(rs.getLong("connected_at")),
                rs.getString("notes")
        );
    }

    private static UUID parseUuid(String raw) {
        return raw == null ? null : UuidNormalizer.parse(raw).orElse(null);
    }

    private static UuidSource parseSource(String raw) {
        if (raw == null || raw.isBlank()) {
            return UuidSource.UNKNOWN;
        }
        return UuidSource.valueOf(raw);
    }
}
