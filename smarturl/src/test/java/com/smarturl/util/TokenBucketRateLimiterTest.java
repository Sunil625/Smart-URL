package com.smarturl.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("TokenBucketRateLimiter Tests")
class TokenBucketRateLimiterTest {

    private TokenBucketRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new TokenBucketRateLimiter();
    }

    @Test
    @DisplayName("first 20 requests are allowed (full bucket)")
    void firstRequestsAllowed() {
        for (int i = 0; i < 20; i++) {
            assertThat(rateLimiter.tryAcquire("192.168.1.1"))
                    .as("Request %d should be allowed", i + 1)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("request after bucket is empty is denied")
    void bucketExhaustedDeniesRequest() {
        String ip = "10.0.0.1";
        // drain the bucket
        for (int i = 0; i < 20; i++) rateLimiter.tryAcquire(ip);

        assertThat(rateLimiter.tryAcquire(ip)).isFalse();
    }

    @Test
    @DisplayName("different IPs have independent buckets")
    void independentBucketsPerIp() {
        String ip1 = "1.1.1.1";
        String ip2 = "2.2.2.2";
        // drain ip1
        for (int i = 0; i < 20; i++) rateLimiter.tryAcquire(ip1);

        assertThat(rateLimiter.tryAcquire(ip1)).isFalse();
        assertThat(rateLimiter.tryAcquire(ip2)).isTrue(); // ip2 is unaffected
    }

    @Test
    @DisplayName("remaining tokens decrements correctly")
    void remainingTokensDecrement() {
        String ip = "3.3.3.3";
        int before = rateLimiter.getRemainingTokens(ip);
        rateLimiter.tryAcquire(ip);
        int after = rateLimiter.getRemainingTokens(ip);
        assertThat(after).isLessThan(before);
    }
}
