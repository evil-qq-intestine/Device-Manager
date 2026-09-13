package com.example.tool.scripttask.service.component;

import com.example.tool.device.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class CryptoServiceTest {

    private static final byte[] KEY = new byte[32];

    static {
        for (int i = 0; i < KEY.length; i++) {
            KEY[i] = (byte) i;
        }
    }

    private final CryptoService crypto = CryptoService.withKey(KEY);

    @Test
    void roundTrip() {
        String plain = "-----BEGIN OPENSSH PRIVATE KEY-----\n中文口令\n-----END-----";
        String encrypted = crypto.encrypt(plain);
        assertNotNull(encrypted);
        assertNotEquals(plain, encrypted);
        assertEquals(plain, crypto.decrypt(encrypted));
    }

    @Test
    void nullHandling() {
        assertNull(crypto.encrypt(null));
        assertNull(crypto.decrypt(null));
    }

    @Test
    void tamperedCipherIsRejected() {
        String encrypted = crypto.encrypt("secret");
        byte[] raw = Base64.getDecoder().decode(encrypted);
        raw[raw.length - 1] ^= 0x01;
        String tampered = Base64.getEncoder().encodeToString(raw);
        assertThrows(BusinessException.class, () -> crypto.decrypt(tampered));
    }

    @Test
    void differentIvProducesDifferentCipher() {
        assertNotEquals(crypto.encrypt("same"), crypto.encrypt("same"));
    }
}
