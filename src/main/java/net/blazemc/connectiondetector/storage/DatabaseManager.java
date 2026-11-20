package net.blazemc.connectiondetector.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import net.blazemc.connectiondetector.config.PluginConfig;

public final class DatabaseManager implements AutoCloseable {

    private final Path databaseFile;
    private final ReentrantLock lock = new ReentrantLock();
    private Connection connection;

    public DatabaseManager(Path dataDirectory, PluginConfig config) {
        this.databaseFile = dataDirectory.resolve(config.database().file()).normalize();
    }

    public Path databaseFile() {
        return databaseFile;
    }

    public void open() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ex) {
            throw new SQLException("SQLite JDBC driver is missing from the plugin JAR.", ex);
        }
        try {
            Files.createDirectories(databaseFile.getParent());
        } catch (Exception ex) {
            throw new SQLException("Unable to create database directory: " + databaseFile.getParent(), ex);
        }
        lock.lock();
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile);
            connection.setAutoCommit(true);
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
                statement.execute("PRAGMA journal_mode = WAL");
                statement.execute("PRAGMA busy_timeout = 5000");
                statement.execute("PRAGMA synchronous = NORMAL");
            }
            initializeSchema(connection);
        } finally {
            lock.unlock();
        }
    }

    public Connection connection() {
        return Objects.requireNonNull(connection, "database is not open");
    }

    public void runInTransaction(SqlWork work) throws SQLException {
        lock.lock();
        try {
            boolean previous = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                work.run(connection);
                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(previous);
            }
        } finally {
            lock.unlock();
        }
    }

    public <T> T callInTransaction(SqlCall<T> work) throws SQLException {
        lock.lock();
        try {
            boolean previous = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(previous);
            }
        } finally {
            lock.unlock();
        }
    }

    public void withLock(SqlWork work) throws SQLException {
        lock.lock();
        try {
            work.run(connection);
        } finally {
            lock.unlock();
        }
    }

    public <T> T withLock(SqlCall<T> work) throws SQLException {
        lock.lock();
        try {
            return work.run(connection);
        } finally {
            lock.unlock();
        }
    }

    private void initializeSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS premium_accounts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        canonical_username TEXT NOT NULL,
                        lowercase_username TEXT NOT NULL UNIQUE,
                        mojang_uuid TEXT NOT NULL UNIQUE,
                        registered_at INTEGER NOT NULL,
                        last_successful_auth INTEGER,
                        last_known_username TEXT NOT NULL,
                        last_refreshed_at INTEGER
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS identity_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        uuid TEXT NOT NULL,
                        uuid_source TEXT NOT NULL,
                        account_type TEXT NOT NULL,
                        last_known_username TEXT NOT NULL,
                        lowercase_username TEXT NOT NULL,
                        first_seen INTEGER NOT NULL,
                        last_seen INTEGER NOT NULL,
                        UNIQUE (uuid, account_type)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS connection_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        username TEXT NOT NULL,
                        uuid TEXT,
                        account_type TEXT NOT NULL,
                        authentication_result TEXT NOT NULL,
                        uuid_source TEXT,
                        premium_registered INTEGER NOT NULL,
                        backend_forwarding_uuid TEXT,
                        connected_at INTEGER NOT NULL,
                        notes TEXT
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_premium_username ON premium_accounts (lowercase_username)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_premium_uuid ON premium_accounts (mojang_uuid)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_identity_username ON identity_records (lowercase_username)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_identity_uuid ON identity_records (uuid)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_history_username ON connection_history (username, connected_at)");
        }
    }

    public boolean isOpen() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException ex) {
            return false;
        }
    }

    @Override
    public void close() {
        lock.lock();
        try {
            if (connection != null) {
                connection.close();
                connection = null;
            }
        } catch (SQLException ignored) {
        } finally {
            lock.unlock();
        }
    }

    @FunctionalInterface
    public interface SqlWork {
        void run(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    public interface SqlCall<T> {
        T run(Connection connection) throws SQLException;
    }
}
