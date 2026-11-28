package com.goldpricemonitor.api.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GoldCredentialCipherTest {
    private final GoldCredentialCipher cipher = new GoldCredentialCipher("12345678901234567890123456789012");

    @Test void usesUniqueAccountSaltsAndAuthenticatedEncryption() {
        String firstSalt = GoldCredentialCipher.newSalt();
        String secondSalt = GoldCredentialCipher.newSalt();
        assertEquals(12, firstSalt.length());
        assertNotEquals(firstSalt, secondSalt);
        String first = cipher.encrypt("支付密码123", firstSalt);
        String second = cipher.encrypt("支付密码123", firstSalt);
        assertNotEquals(first, second);
        assertEquals("支付密码123", cipher.decrypt(first, firstSalt));
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(first, secondSalt));
        assertThrows(IllegalArgumentException.class, () -> new GoldCredentialCipher("short"));
    }
}
