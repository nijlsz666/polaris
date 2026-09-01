package com.polaris.mes.security;

import org.springframework.stereotype.Component;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** PBKDF2 password storage without keeping plaintext passwords in the database. */
@Component
public class PasswordHasher {
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 210_000;
    private static final int KEY_BITS = 256;
    private static final int SALT_BYTES = 16;
    private final SecureRandom random = new SecureRandom();

    public String hash(String password) {
        if (password == null || password.isBlank()) throw new IllegalArgumentException("密码不能为空");
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        return encode(salt, derive(password.toCharArray(), salt, ITERATIONS));
    }

    public boolean matches(String password, String encoded) {
        if (password == null || encoded == null || !encoded.startsWith("pbkdf2$")) return false;
        try {
            String[] parts = encoded.split("\\$", -1);
            if (parts.length != 4) return false;
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getUrlDecoder().decode(parts[2]);
            byte[] expected = Base64.getUrlDecoder().decode(parts[3]);
            return MessageDigest.isEqual(expected, derive(password.toCharArray(), salt, iterations));
        } catch (RuntimeException ex) {
            return false;
        }
    }

    public boolean isLegacyPlaintext(String stored) {
        return stored != null && !stored.startsWith("pbkdf2$");
    }

    private byte[] derive(char[] password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("无法生成密码摘要", ex);
        }
    }

    private static String encode(byte[] salt, byte[] digest) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return "pbkdf2$" + ITERATIONS + "$" + encoder.encodeToString(salt) + "$" + encoder.encodeToString(digest);
    }
}
