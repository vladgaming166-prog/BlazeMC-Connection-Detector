package net.blazemc.connectiondetector.auth;

import java.util.regex.Pattern;

public final class UsernameValidator {

    private static final Pattern MINECRAFT_USERNAME = Pattern.compile("^[A-Za-z0-9_]{3,16}$");

    private UsernameValidator() {
    }

    public static boolean isValid(String username) {
        return username != null && MINECRAFT_USERNAME.matcher(username).matches();
    }

    public static String normalizeKey(String username) {
        if (username == null) {
            return "";
        }
        return username.toLowerCase();
    }
}
