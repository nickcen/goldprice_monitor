package com.goldpricemonitor.api.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Encrypts recoverable Gold credentials; each account has its own key derivation salt. */
final class GoldCredentialCipher {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private final String systemSalt;

    GoldCredentialCipher(String systemSalt) {
        if (systemSalt == null || !systemSalt.matches("[A-Za-z0-9]{32}"))
            throw new IllegalArgumentException("APP_SYSTEM_SALT must be a 32-character alphanumeric secret");
        this.systemSalt = systemSalt;
    }

    static String newSalt() {
        StringBuilder value = new StringBuilder(12);
        for (int i = 0; i < 12; i++) value.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        return value.toString();
    }

    private SecretKeySpec key(String accountSalt) throws Exception {
        if (accountSalt == null || !accountSalt.matches("[A-Za-z0-9]{12}"))
            throw new IllegalStateException("Gold account salt is missing or invalid");
        byte[] digest = MessageDigest.getInstance("SHA-256").digest((systemSalt + ":" + accountSalt).getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(digest, "AES");
    }

    String encrypt(String plain, String accountSalt) {
        if (plain == null) return null;
        try {
            byte[] nonce = new byte[12];
            RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(accountSalt), new GCMParameterSpec(128, nonce));
            return "v1:" + Base64.getEncoder().encodeToString(nonce) + ":" + Base64.getEncoder().encodeToString(cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException("Gold credential encryption failed", e); }
    }

    String decrypt(String stored, String accountSalt) {
        if (stored == null) return null;
        if (!stored.startsWith("v1:")) throw new IllegalStateException("Gold credential is not encrypted");
        try {
            String[] parts = stored.split(":", 3);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(accountSalt), new GCMParameterSpec(128, Base64.getDecoder().decode(parts[1])));
            return new String(cipher.doFinal(Base64.getDecoder().decode(parts[2])), StandardCharsets.UTF_8);
        } catch (Exception e) { throw new IllegalStateException("Gold credential decryption failed", e); }
    }
}
