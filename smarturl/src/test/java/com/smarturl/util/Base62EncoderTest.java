package com.smarturl.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Base62Encoder Tests")
class Base62EncoderTest {

    private Base62Encoder encoder;

    @BeforeEach
    void setUp() {
        encoder = new Base62Encoder();
    }

    @Test
    @DisplayName("encode and decode are inverse operations")
    void encodeDecodeRoundTrip() {
        long[] testIds = {1, 100, 999, 10_000, 1_000_000, Long.MAX_VALUE / 100};
        for (long id : testIds) {
            String code = encoder.encode(id);
            long decoded = encoder.decode(code);
            assertThat(decoded).as("Round-trip for id=%d", id).isEqualTo(id);
        }
    }

    @Test
    @DisplayName("all codes are exactly 7 characters")
    void codesAreSevenChars() {
        assertThat(encoder.encode(1)).hasSize(7);
        assertThat(encoder.encode(999_999)).hasSize(7);
        assertThat(encoder.encode(62)).hasSize(7);
    }

    @Test
    @DisplayName("different IDs produce different codes")
    void uniqueCodes() {
        String code1 = encoder.encode(1);
        String code2 = encoder.encode(2);
        assertThat(code1).isNotEqualTo(code2);
    }

    @Test
    @DisplayName("encode(0) returns all zeros padded")
    void encodeZero() {
        assertThat(encoder.encode(0)).isEqualTo("0000000");
    }

    @Test
    @DisplayName("invalid negative ID throws exception")
    void negativeIdThrows() {
        assertThatThrownBy(() -> encoder.encode(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("invalid character in code throws exception")
    void invalidCharThrows() {
        assertThatThrownBy(() -> encoder.decode("abc!def"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("isValid returns false for null/blank")
    void isValidEdgeCases() {
        assertThat(encoder.isValid(null)).isFalse();
        assertThat(encoder.isValid("")).isFalse();
        assertThat(encoder.isValid("  ")).isFalse();
    }

    @Test
    @DisplayName("isValid returns true for well-formed codes")
    void isValidHappyPath() {
        assertThat(encoder.isValid("abc1234")).isTrue();
        assertThat(encoder.isValid("AAAAAAA")).isTrue();
    }
}
