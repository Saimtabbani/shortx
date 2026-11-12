package com.shortx.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Extracted here rather than left as a private method on AuthServiceImpl
 * (where it originated, for refresh-token hashing) because this
 * milestone's click-analytics recorder needs the exact same operation for
 * IP-address hashing (see V6 migration / click_events.ip_hash). One
 * implementation, two call sites, instead of two copies quietly drifting
 * apart.
 */
public final class HashUtils {

    private HashUtils() {
        // Non-instantiable utility class.
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed available on every standard JVM - this
            // branch is unreachable in practice.
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }
}
