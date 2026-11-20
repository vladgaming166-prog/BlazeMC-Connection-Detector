package net.blazemc.connectiondetector.auth;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.blazemc.connectiondetector.model.PremiumAccount;

public interface PremiumAccountStore {

    Optional<PremiumAccount> findByUsername(String username);

    Optional<PremiumAccount> findByUuid(UUID mojangUuid);

    List<PremiumAccount> listAll();

    void insert(PremiumAccount account);

    void update(PremiumAccount account);

    boolean deleteByUsername(String username);
}
