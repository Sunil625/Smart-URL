package com.smarturl.service;

import com.smarturl.dto.AnalyticsResponse;
import com.smarturl.entity.AnalyticsEvent;
import com.smarturl.repository.AnalyticsEventRepository;
import com.smarturl.repository.UrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Async analytics pipeline — Producer-Consumer pattern.
 *
 * The hot redirect path calls trackClick() which does a non-blocking queue.offer().
 * A @Scheduled background thread drains the queue every 5 seconds and batch-inserts.
 *
 * DSA highlights:
 *   - LinkedBlockingQueue: bounded, thread-safe producer-consumer queue
 *   - Batch processing: drainTo() pulls up to BATCH_SIZE items atomically
 *   - Non-blocking offer(): redirect latency is never affected by DB write speed
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private static final int QUEUE_CAPACITY = 10_000;
    private static final int BATCH_SIZE     = 100;

    private final AnalyticsEventRepository analyticsRepo;
    private final UrlRepository            urlRepository;

    // Bounded in-memory analytics buffer
    private final LinkedBlockingQueue<AnalyticsEvent> eventQueue =
            new LinkedBlockingQueue<>(QUEUE_CAPACITY);

    /**
     * Called on every redirect. Non-blocking — offer() returns false if queue full.
     * Keeping this method fast ensures redirect latency stays low regardless of write load.
     */
    public void trackClick(String shortCode, String ipHash, String referrer, String userAgent) {
        AnalyticsEvent event = AnalyticsEvent.builder()
                .shortCode(shortCode)
                .ipHash(ipHash)
                .referrer(truncate(referrer, 512))
                .userAgent(truncate(userAgent, 512))
                .clickedAt(LocalDateTime.now())
                .build();

        boolean accepted = eventQueue.offer(event);
        if (!accepted) {
            log.warn("Analytics queue at capacity — dropping event for shortCode='{}'", shortCode);
        }
    }

    /**
     * Background worker: drains up to BATCH_SIZE events every 5 seconds and batch-inserts.
     * Batch inserts are ~10-50x faster than individual inserts for analytics workloads.
     */
    @Scheduled(fixedDelay = 5_000)
    @Transactional
    public void flushAnalytics() {
        List<AnalyticsEvent> batch = new ArrayList<>(BATCH_SIZE);
        int drained = eventQueue.drainTo(batch, BATCH_SIZE);

        if (drained > 0) {
            analyticsRepo.saveAll(batch);
            log.debug("Flushed {} analytics events to DB", drained);
        }
    }

    /**
     * Returns aggregated analytics for a short code.
     */
    @Transactional(readOnly = true)
    public AnalyticsResponse getAnalytics(String shortCode) {
        var url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new RuntimeException("URL not found: " + shortCode));

        LocalDateTime now = LocalDateTime.now();
        Long clicks24h = analyticsRepo.countClicksSince(shortCode, now.minusHours(24));
        Long clicks7d  = analyticsRepo.countClicksSince(shortCode, now.minusDays(7));

        List<Object[]> rawReferrers = analyticsRepo.findTopReferrers(shortCode);
        Map<String, Long> topReferrers = new LinkedHashMap<>();
        for (Object[] row : rawReferrers) {
            String ref = row[0] != null ? (String) row[0] : "direct";
            Long   cnt = (Long) row[1];
            topReferrers.put(ref, cnt);
            if (topReferrers.size() >= 5) break;
        }

        return AnalyticsResponse.builder()
                .shortCode(shortCode)
                .longUrl(url.getLongUrl())
                .totalClicks(url.getClickCount())
                .clicksLast24h(clicks24h)
                .clicksLast7d(clicks7d)
                .topReferrers(topReferrers)
                .build();
    }

    /** Exposed for health-check endpoint and tests. */
    public int getQueueSize() {
        return eventQueue.size();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
