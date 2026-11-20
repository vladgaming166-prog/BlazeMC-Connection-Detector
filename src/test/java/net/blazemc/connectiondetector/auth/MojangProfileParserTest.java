package net.blazemc.connectiondetector.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MojangProfileParserTest {

    @Test
    void parsesOfficialMojangPayload() {
        String body = "{\"id\":\"069a79f444e94726a5befca90e38aaf5\",\"name\":\"Notch\"}";
        MojangProfile profile = MojangProfileParser.parse(body).orElseThrow();
        assertEquals("Notch", profile.canonicalUsername());
        assertEquals("069a79f4-44e9-4726-a5be-fca90e38aaf5", profile.uuid().toString());
    }

    @Test
    void rejectsMalformedPayloads() {
        assertTrue(MojangProfileParser.parse("{").isEmpty());
        assertTrue(MojangProfileParser.parse("{\"name\":\"Notch\"}").isEmpty());
        assertTrue(MojangProfileParser.parse("{\"id\":\"bad\",\"name\":\"Notch\"}").isEmpty());
    }
}
