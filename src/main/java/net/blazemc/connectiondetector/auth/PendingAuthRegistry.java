package net.blazemc.connectiondetector.auth;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class PendingAuthRegistry {

    private final ConcurrentHashMap<String, PendingAuthSession> byConnection = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> connectionByUsername = new ConcurrentHashMap<>();

    public void put(PendingAuthSession session) {
        byConnection.put(session.connectionId(), session);
        connectionByUsername.put(UsernameValidator.normalizeKey(session.username()), session.connectionId());
    }

    public Optional<PendingAuthSession> get(String connectionId) {
        return Optional.ofNullable(byConnection.get(connectionId));
    }

    public Optional<PendingAuthSession> remove(String connectionId) {
        PendingAuthSession removed = byConnection.remove(connectionId);
        if (removed != null) {
            connectionByUsername.remove(UsernameValidator.normalizeKey(removed.username()), connectionId);
        }
        return Optional.ofNullable(removed);
    }

    public Optional<PendingAuthSession> findByUsername(String username) {
        String connectionId = connectionByUsername.get(UsernameValidator.normalizeKey(username));
        if (connectionId == null) {
            return Optional.empty();
        }
        return get(connectionId);
    }

    public void clear() {
        byConnection.clear();
        connectionByUsername.clear();
    }
}
