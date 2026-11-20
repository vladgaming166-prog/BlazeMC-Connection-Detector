package net.blazemc.connectiondetector.auth;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class UuidNormalizer {

    private UuidNormalizer() {
    }

    public static UUID fromMojang(String raw) {
        return parse(raw).orElseThrow(() -> new IllegalArgumentException("Invalid Mojang UUID: " + raw));
    }

    public static Optional<UUID> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String value = raw.trim().toLowerCase(Locale.ROOT).replace("-", "");
        if (value.length() != 32 || !value.chars().allMatch(ch -> Character.digit(ch, 16) >= 0)) {
            return Optional.empty();
        }
        String dashed = value.substring(0, 8)
                + "-" + value.substring(8, 12)
                + "-" + value.substring(12, 16)
                + "-" + value.substring(16, 20)
                + "-" + value.substring(20, 32);
        try {
            return Optional.of(UUID.fromString(dashed));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public static String toDashed(UUID uuid) {
        return uuid.toString().toLowerCase(Locale.ROOT);
    }

    public static String toUndashed(UUID uuid) {
        return uuid.toString().toLowerCase(Locale.ROOT).replace("-", "");
    }

    public static boolean equals(UUID left, UUID right) {
        return left != null && left.equals(right);
    }

    public static UUID offlineUuid(String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
    }

    public static boolean isOfflineStyleUuid(UUID uuid, String username) {
        return uuid != null && uuid.equals(offlineUuid(username));
    }
}
