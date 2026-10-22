package com.managementcontrol.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Робота з токенами пристроїв: генерація та хешування.
 * У базу зберігається лише SHA-256 хеш — сам токен агент показує один раз при реєстрації.
 */
public final class TokenService {

    /** Префікс токена пристрою — дозволяє відрізнити його від admin JWT. */
    public static final String DEVICE_TOKEN_PREFIX = "mc_dev_";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private TokenService() {
    }

    public static String generateDeviceToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return DEVICE_TOKEN_PREFIX + ENCODER.encodeToString(bytes);
    }

    public static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return hex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 недоступний", e);
        }
    }

    public static boolean isDeviceToken(String token) {
        return token != null && token.startsWith(DEVICE_TOKEN_PREFIX);
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
