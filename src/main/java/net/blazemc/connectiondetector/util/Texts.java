package net.blazemc.connectiondetector.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

public final class Texts {

    public static final TextColor BLAZE = NamedTextColor.GOLD;
    public static final TextColor MUTED = NamedTextColor.GRAY;
    public static final TextColor OK = NamedTextColor.GREEN;
    public static final TextColor WARN = NamedTextColor.YELLOW;
    public static final TextColor ERROR = NamedTextColor.RED;
    public static final TextColor INFO = NamedTextColor.AQUA;

    private Texts() {
    }

    public static Component prefix() {
        return Component.text("[BlazeMC Connection Detector] ", BLAZE);
    }

    public static Component line(String message, TextColor color) {
        return prefix().append(Component.text(message, color));
    }

    public static Component info(String message) {
        return line(message, INFO);
    }

    public static Component ok(String message) {
        return line(message, OK);
    }

    public static Component warn(String message) {
        return line(message, WARN);
    }

    public static Component error(String message) {
        return line(message, ERROR);
    }

    public static Component muted(String message) {
        return line(message, MUTED);
    }

    public static Component kick(String message) {
        return Component.text("[BlazeMC Connection Detector]\n", BLAZE)
                .append(Component.text(message, ERROR));
    }
}
