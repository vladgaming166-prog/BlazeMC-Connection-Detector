package net.blazemc.connectiondetector.model;

public enum ConnectionState {
    UNKNOWN,
    PREMIUM_CANDIDATE,
    PREMIUM_VERIFIED,
    CRACKED_CONFIRMED,
    AUTHENTICATION_FAILED,
    MOJANG_API_UNAVAILABLE
}
