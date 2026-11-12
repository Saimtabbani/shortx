package com.shortx.util;

/**
 * Encodes a positive long into a Base62 ([0-9a-zA-Z]) string and back.
 * Used to turn a Snowflake-generated short_urls.id into the public-facing
 * short_code — Base62 needs no URL escaping (unlike Base64's '+', '/', '=')
 * and is shorter than decimal or hex for the same value.
 *
 * <p>Stateless and thread-safe; a plain utility class rather than a Spring
 * bean since it holds no configuration or state — every call is a pure
 * function of its input.
 */
public final class Base62Encoder {

    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int BASE = ALPHABET.length();

    private Base62Encoder() {
        // Non-instantiable utility class.
    }

    public static String encode(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("Cannot Base62-encode a negative value: " + value);
        }
        if (value == 0) {
            return String.valueOf(ALPHABET.charAt(0));
        }

        StringBuilder sb = new StringBuilder();
        long remaining = value;
        while (remaining > 0) {
            int digit = (int) (remaining % BASE);
            sb.append(ALPHABET.charAt(digit));
            remaining /= BASE;
        }
        // Digits were appended least-significant first.
        return sb.reverse().toString();
    }

    public static long decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            throw new IllegalArgumentException("Cannot decode a null/empty Base62 string.");
        }

        long result = 0;
        for (int i = 0; i < encoded.length(); i++) {
            int digit = ALPHABET.indexOf(encoded.charAt(i));
            if (digit < 0) {
                throw new IllegalArgumentException("Invalid Base62 character '" + encoded.charAt(i) + "' in: " + encoded);
            }
            result = result * BASE + digit;
        }
        return result;
    }
}
