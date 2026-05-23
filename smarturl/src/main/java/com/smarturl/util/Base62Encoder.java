package com.smarturl.util;

import org.springframework.stereotype.Component;

/**
 * Base62 Encoder — core DSA algorithm of this project.
 *
 * Converts a long counter to a short alphanumeric string using base-62 encoding.
 * Characters: 0-9 (10) + a-z (26) + A-Z (26) = 62 characters
 *
 * Example: encode(125) → "cb"
 * A 7-character Base62 string can represent 62^7 = ~3.5 trillion unique URLs.
 *
 * Time complexity: O(log₆₂ n) for encode, O(k) for decode where k = code length
 * Space complexity: O(log₆₂ n)
 */
@Component
public class Base62Encoder {

    private static final String CHARSET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE = 62;
    private static final int DEFAULT_CODE_LENGTH = 7;

    /**
     * Encodes a long ID to a Base62 string.
     * Pads to DEFAULT_CODE_LENGTH with leading '0' chars for consistent length.
     */
    public String encode(long id) {
        if (id < 0) throw new IllegalArgumentException("ID must be non-negative, got: " + id);
        if (id == 0) return padLeft("0", DEFAULT_CODE_LENGTH);

        StringBuilder sb = new StringBuilder();
        long num = id;

        while (num > 0) {
            sb.append(CHARSET.charAt((int)(num % BASE)));
            num /= BASE;
        }

        // Reverse — we built it least-significant-digit first
        return padLeft(sb.reverse().toString(), DEFAULT_CODE_LENGTH);
    }

    /**
     * Decodes a Base62 string back to a long ID.
     */
    public long decode(String code) {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("Code cannot be blank");

        long result = 0;
        for (char c : code.toCharArray()) {
            int digitValue = CHARSET.indexOf(c);
            if (digitValue == -1) throw new IllegalArgumentException("Invalid character in code: " + c);
            result = result * BASE + digitValue;
        }
        return result;
    }

    /**
     * Validates that a string is a valid Base62 code.
     */
    public boolean isValid(String code) {
        if (code == null || code.isBlank()) return false;
        for (char c : code.toCharArray()) {
            if (CHARSET.indexOf(c) == -1) return false;
        }
        return true;
    }

    private String padLeft(String s, int length) {
        while (s.length() < length) {
            s = "0" + s;
        }
        return s;
    }
}
