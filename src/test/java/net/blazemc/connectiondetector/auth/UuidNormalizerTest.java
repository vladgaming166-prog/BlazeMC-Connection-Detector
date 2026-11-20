package net.blazemc.connectiondetector.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidNormalizerTest {

    @Test
    void parsesDashedAndUndashedUuids() {
        UUID uuid = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
        assertEquals(uuid, UuidNormalizer.fromMojang("069a79f444e94726a5befca90e38aaf5"));
        assertEquals(uuid, UuidNormalizer.fromMojang("069A79F4-44E9-4726-A5BE-FCA90E38AAF5"));
        assertEquals("069a79f444e94726a5befca90e38aaf5", UuidNormalizer.toUndashed(uuid));
    }

    @Test
    void rejectsMalformedValues() {
        assertTrue(UuidNormalizer.parse("not-a-uuid").isEmpty());
        assertTrue(UuidNormalizer.parse("").isEmpty());
        assertTrue(UuidNormalizer.parse(null).isEmpty());
    }

    @Test
    void offlineUuidMatchesVelocityStyle() {
        UUID offline = UuidNormalizer.offlineUuid("Blaz3Craft");
        assertTrue(UuidNormalizer.isOfflineStyleUuid(offline, "Blaz3Craft"));
    }
}
