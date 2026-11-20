package net.blazemc.connectiondetector.auth;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MojangProfileParser {

    private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern NAME = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");

    private MojangProfileParser() {
    }

    public static Optional<MojangProfile> parse(String body) {
        if (body == null || body.isBlank()) {
            return Optional.empty();
        }
        Matcher idMatcher = ID.matcher(body);
        Matcher nameMatcher = NAME.matcher(body);
        if (!idMatcher.find() || !nameMatcher.find()) {
            return Optional.empty();
        }
        Optional<UUID> uuid = UuidNormalizer.parse(idMatcher.group(1));
        String name = nameMatcher.group(1);
        if (uuid.isEmpty() || !UsernameValidator.isValid(name)) {
            return Optional.empty();
        }
        return Optional.of(new MojangProfile(name, uuid.get()));
    }
}
