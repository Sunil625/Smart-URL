package com.smarturl.service;

import com.smarturl.dto.ShortenRequest;
import com.smarturl.dto.ShortenResponse;
import com.smarturl.entity.Url;
import com.smarturl.entity.User;
import com.smarturl.exception.ShortCodeAlreadyExistsException;
import com.smarturl.exception.UrlNotFoundException;
import com.smarturl.repository.UrlRepository;
import com.smarturl.util.Base62Encoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Core URL shortening service.
 *
 * Short code generation strategy:
 *   - Auto-alias: save URL first to get DB auto-increment ID, then Base62-encode the ID.
 *     This gives collision-free codes without a separate counter table.
 *   - Custom alias: validate uniqueness, then use as-is.
 *
 * Cache-aside via @Cacheable on resolve() — Redis is checked first; DB on miss.
 * @CacheEvict on delete() keeps Redis consistent.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UrlService {

    private final UrlRepository urlRepository;
    private final Base62Encoder base62Encoder;
    private final AnalyticsService analyticsService;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    /**
     * Shorten a URL.
     * Step 1: persist with a temp code to get the auto-increment primary key.
     * Step 2: compute real code = Base62(id).
     * Step 3: update the record with the real code.
     */
    @Transactional
    public ShortenResponse shorten(ShortenRequest request, User user) {
        String shortCode;

        if (request.getCustomAlias() != null && !request.getCustomAlias().isBlank()) {
            shortCode = request.getCustomAlias().trim();
            if (urlRepository.findByShortCode(shortCode).isPresent()) {
                throw new ShortCodeAlreadyExistsException(
                        "Alias '" + shortCode + "' is already taken. Choose a different one.");
            }
        } else {
            shortCode = null; // will be set after save
        }

        LocalDateTime expiresAt = null;
        if (request.getExpiresInDays() != null && request.getExpiresInDays() > 0) {
            expiresAt = LocalDateTime.now().plusDays(request.getExpiresInDays());
        }

        // Save with temp code so JPA assigns the ID
        Url url = Url.builder()
                .longUrl(request.getLongUrl())
                .shortCode(shortCode != null ? shortCode : "TEMP")
                .user(user)
                .expiresAt(expiresAt)
                .build();

        Url saved = urlRepository.save(url);

        // If auto-generated, now compute real code from the DB-assigned ID
        if (shortCode == null) {
            String realCode = base62Encoder.encode(saved.getId());
            saved.setShortCode(realCode);
            saved = urlRepository.save(saved);
        }

        log.info("Shortened: {} -> {}", request.getLongUrl(), saved.getShortCode());
        return toResponse(saved);
    }

    /**
     * Resolve short code to long URL.
     * @Cacheable = Redis cache-aside. On cache miss, fetches from DB and caches result.
     * Cache key is the shortCode string. TTL configured in RedisConfig (24h).
     */
    @Cacheable(value = "urls", key = "#shortCode")
    @Transactional(readOnly = true)
    public String resolve(String shortCode) {
        log.debug("Cache MISS for '{}' — querying DB", shortCode);

        Url url = urlRepository.findByShortCodeAndActiveTrue(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(
                        "No URL found for code: " + shortCode));

        if (url.isExpired()) {
            throw new UrlNotFoundException("Short URL '" + shortCode + "' has expired.");
        }

        return url.getLongUrl();
    }

    /**
     * Increment click counter in DB + enqueue analytics event (non-blocking).
     */
    @Transactional
    public void recordClick(String shortCode, String ipHash, String referrer, String userAgent) {
        urlRepository.incrementClickCount(shortCode);
        analyticsService.trackClick(shortCode, ipHash, referrer, userAgent);
    }

    /**
     * Soft-delete a URL and evict from Redis cache.
     */
    @CacheEvict(value = "urls", key = "#shortCode")
    @Transactional
    public void deleteUrl(String shortCode, User user) {
        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException("URL not found: " + shortCode));

        if (url.getUser() == null || !url.getUser().getId().equals(user.getId())) {
            throw new SecurityException("You do not have permission to delete this URL.");
        }

        url.setActive(false);
        urlRepository.save(url);
        log.info("Deleted (soft) URL: {}", shortCode);
    }

    /**
     * List all active URLs for a user, paginated.
     */
    @Transactional(readOnly = true)
    public Page<ShortenResponse> getUserUrls(User user, Pageable pageable) {
        return urlRepository.findByUserAndActiveTrue(user, pageable)
                .map(this::toResponse);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private ShortenResponse toResponse(Url url) {
        return ShortenResponse.builder()
                .shortCode(url.getShortCode())
                .shortUrl(baseUrl + "/" + url.getShortCode())
                .longUrl(url.getLongUrl())
                .createdAt(url.getCreatedAt())
                .expiresAt(url.getExpiresAt())
                .clickCount(url.getClickCount())
                .build();
    }
}
