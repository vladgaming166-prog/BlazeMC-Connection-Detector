package net.blazemc.connectiondetector.auth;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.blazemc.connectiondetector.model.PremiumAccount;

public final class InMemoryPremiumAccountStore implements PremiumAccountStore {

    private final ConcurrentHashMap<UUID, PremiumAccount> byUuid = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UUID> byUsername = new ConcurrentHashMap<>();

    @Override
    public Optional<PremiumAccount> findByUsername(String username) {
        UUID uuid = byUsername.get(UsernameValidator.normalizeKey(username));
        return uuid == null ? Optional.empty() : Optional.ofNullable(byUuid.get(uuid));
    }

    @Override
    public Optional<PremiumAccount> findByUuid(UUID mojangUuid) {
        return Optional.ofNullable(byUuid.get(mojangUuid));
    }

    @Override
    public List<PremiumAccount> listAll() {
        return new ArrayList<>(byUuid.values());
    }

    @Override
    public void insert(PremiumAccount account) {
        byUuid.put(account.mojangUuid(), account);
        byUsername.put(account.lowercaseUsername(), account.mojangUuid());
    }

    @Override
    public void update(PremiumAccount account) {
        byUsername.entrySet().removeIf(entry -> entry.getValue().equals(account.mojangUuid()));
        insert(account);
    }

    @Override
    public boolean deleteByUsername(String username) {
        UUID uuid = byUsername.remove(UsernameValidator.normalizeKey(username));
        if (uuid == null) {
            return false;
        }
        byUuid.remove(uuid);
        return true;
    }
}
