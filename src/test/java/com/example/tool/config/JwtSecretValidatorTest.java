package com.example.tool.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtSecretValidatorTest {

    private static final String PLACEHOLDER = "your-256-bit-secret-key-here-at-least-32-characters";
    private static final String STRONG = "0123456789abcdef0123456789abcdef";

    @Test
    void rejectsPlaceholderSecret() {
        assertThrows(IllegalStateException.class, () -> new JwtSecretValidator(PLACEHOLDER, true).run(null));
    }

    @Test
    void rejectsShortSecret() {
        assertThrows(IllegalStateException.class, () -> new JwtSecretValidator("short", true).run(null));
    }

    @Test
    void rejectsBlankSecret() {
        assertThrows(IllegalStateException.class, () -> new JwtSecretValidator("  ", true).run(null));
    }

    @Test
    void acceptsStrongSecret() {
        assertDoesNotThrow(() -> new JwtSecretValidator(STRONG, true).run(null));
    }

    @Test
    void warnsInsteadOfFailingWhenDisabled() {
        assertDoesNotThrow(() -> new JwtSecretValidator(PLACEHOLDER, false).run(null));
    }
}
