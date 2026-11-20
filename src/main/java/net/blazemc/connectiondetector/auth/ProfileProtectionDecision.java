package net.blazemc.connectiondetector.auth;

import java.util.Optional;
import java.util.UUID;
import net.blazemc.connectiondetector.model.UuidSource;

public final class ProfileProtectionDecision {

    private final UUID uuidToKeep;
    private final UuidSource uuidSource;
    private final boolean restoreOriginal;
    private final boolean leaveUntouched;

    private ProfileProtectionDecision(
            UUID uuidToKeep,
            UuidSource uuidSource,
            boolean restoreOriginal,
            boolean leaveUntouched
    ) {
        this.uuidToKeep = uuidToKeep;
        this.uuidSource = uuidSource;
        this.restoreOriginal = restoreOriginal;
        this.leaveUntouched = leaveUntouched;
    }

    public static ProfileProtectionDecision keepPremiumUuid(UUID uuid) {
        return new ProfileProtectionDecision(uuid, UuidSource.MOJANG_SESSION, true, false);
    }

    public static ProfileProtectionDecision leaveUntouched() {
        return new ProfileProtectionDecision(null, UuidSource.UNKNOWN, false, true);
    }

    public Optional<UUID> uuidToKeep() {
        return Optional.ofNullable(uuidToKeep);
    }

    public UuidSource uuidSource() {
        return uuidSource;
    }

    public boolean restoreOriginal() {
        return restoreOriginal;
    }

    public boolean shouldLeaveUntouched() {
        return leaveUntouched;
    }
}
