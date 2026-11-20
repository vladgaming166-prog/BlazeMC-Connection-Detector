package net.blazemc.connectiondetector.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UsernameValidatorTest {

    @Test
    void acceptsLegalMinecraftNames() {
        assertTrue(UsernameValidator.isValid("Blaz3Craft"));
        assertTrue(UsernameValidator.isValid("abc"));
        assertTrue(UsernameValidator.isValid("A_Very_LongName1"));
    }

    @Test
    void rejectsIllegalMinecraftNames() {
        assertFalse(UsernameValidator.isValid("ab"));
        assertFalse(UsernameValidator.isValid("thisnameiswaytoolong"));
        assertFalse(UsernameValidator.isValid("Bad-Name"));
        assertFalse(UsernameValidator.isValid(""));
        assertFalse(UsernameValidator.isValid(null));
    }
}
