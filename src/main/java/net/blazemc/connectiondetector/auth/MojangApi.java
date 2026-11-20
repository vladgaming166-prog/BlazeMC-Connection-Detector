package net.blazemc.connectiondetector.auth;

public interface MojangApi {

    MojangLookupResult lookupByUsername(String username);
}
