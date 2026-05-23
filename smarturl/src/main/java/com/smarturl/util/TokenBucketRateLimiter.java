package com.smarturl.util;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Token Bucket Rate Limiter — classic DSA algorithm applied to production code.
 *
 * Each IP gets a "bucket" that holds up to MAX_TOKENS tokens.
 * Tokens refill at REFILL_RATE per second.
 * Each request consumes 1 token. If bucket is empty → 429 Too Many Requests.
 *
 * Thread-safe via ConcurrentHashMap + AtomicInteger.
 * Time complexity: O(1) per check. Space: O(n) where n = unique IPs.
 */
@Component
public class TokenBucketRateLimiter {

    private static final int MAX_TOKENS = 20;        // max burst
    private static final int REFILL_RATE = 10;       // tokens per second
    private static final long REFILL_INTERVAL_MS = 1000L;

    private record Bucket(AtomicInteger tokens, long lastRefillTime) {}

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * Returns true if request is allowed, false if rate limited.
     */
    public boolean tryAcquire(String clientKey) {
        Bucket bucket = buckets.computeIfAbsent(clientKey,
                k -> new Bucket(new AtomicInteger(MAX_TOKENS), System.currentTimeMillis()));

        refillIfNeeded(bucket, clientKey);

        // Atomic decrement — only if tokens > 0
        int current;
        do {
            current = bucket.tokens().get();
            if (current <= 0) return false;
        } while (!bucket.tokens().compareAndSet(current, current - 1));

        return true;
    }

    private void refillIfNeeded(Bucket bucket, String key) {
        long now = System.currentTimeMillis();
        long elapsed = now - bucket.lastRefillTime();

        if (elapsed >= REFILL_INTERVAL_MS) {
            long intervals = elapsed / REFILL_INTERVAL_MS;
            int tokensToAdd = (int) Math.min(intervals * REFILL_RATE, MAX_TOKENS);

            // Replace bucket with fresh refill time (simple approach, avoids synchronized)
            Bucket refilled = new Bucket(
                    new AtomicInteger(Math.min(bucket.tokens().get() + tokensToAdd, MAX_TOKENS)),
                    now
            );
            buckets.put(key, refilled);
        }
    }

    /** For monitoring/testing */
    public int getRemainingTokens(String clientKey) {
        Bucket b = buckets.get(clientKey);
        return b == null ? MAX_TOKENS : b.tokens().get();
    }

    /** Cleans up stale buckets (called by scheduled task) */
    public void evictStaleBuckets(long maxAgeMs) {
        long cutoff = System.currentTimeMillis() - maxAgeMs;
        buckets.entrySet().removeIf(e -> e.getValue().lastRefillTime() < cutoff);
    }
}
