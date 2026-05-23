package com.smarturl.controller;

import com.smarturl.dto.AnalyticsResponse;
import com.smarturl.dto.ShortenRequest;
import com.smarturl.dto.ShortenResponse;
import com.smarturl.entity.User;
import com.smarturl.service.AnalyticsService;
import com.smarturl.service.UrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@RestController
@RequiredArgsConstructor
@Tag(name = "URL Shortener", description = "Shorten, resolve, manage URLs")
public class UrlController {

    private final UrlService     urlService;
    private final AnalyticsService analyticsService;

    // ── Public ──────────────────────────────────────────────────────────────

    /**
     * Redirect: GET /{code} → HTTP 302 to long URL.
     * This is the hot path — cached in Redis, rate-limited at interceptor level.
     */
    @GetMapping("/{shortCode:[a-zA-Z0-9]{3,10}}")
    @Operation(summary = "Redirect to original URL")
    public ResponseEntity<Void> redirect(
            @PathVariable String shortCode,
            HttpServletRequest request) {

        String longUrl   = urlService.resolve(shortCode);
        String ipHash    = hashIp(extractIp(request));
        String referrer  = request.getHeader("Referer");
        String userAgent = request.getHeader("User-Agent");

        // Non-blocking — enqueues to LinkedBlockingQueue, returns immediately
        urlService.recordClick(shortCode, ipHash, referrer, userAgent);

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(longUrl))
                .build();
    }

    // ── Authenticated ────────────────────────────────────────────────────────

    @PostMapping("/api/urls")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Shorten a URL", security = @SecurityRequirement(name = "bearerAuth"))
    public ShortenResponse shorten(
            @Valid @RequestBody ShortenRequest request,
            @AuthenticationPrincipal User user) {
        return urlService.shorten(request, user);
    }

    @GetMapping("/api/urls")
    @Operation(summary = "List my URLs (paginated)", security = @SecurityRequirement(name = "bearerAuth"))
    public Page<ShortenResponse> listUrls(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal User user) {
        return urlService.getUserUrls(user,
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
    }

    @GetMapping("/api/urls/{shortCode}/analytics")
    @Operation(summary = "Get click analytics for a URL", security = @SecurityRequirement(name = "bearerAuth"))
    public AnalyticsResponse getAnalytics(@PathVariable String shortCode) {
        return analyticsService.getAnalytics(shortCode);
    }

    @DeleteMapping("/api/urls/{shortCode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a URL (soft delete)", security = @SecurityRequirement(name = "bearerAuth"))
    public void deleteUrl(
            @PathVariable String shortCode,
            @AuthenticationPrincipal User user) {
        urlService.deleteUrl(shortCode, user);
    }

    // ── Health ───────────────────────────────────────────────────────────────

    @GetMapping("/api/health")
    @Operation(summary = "Health check + queue depth")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok(
                "{\"status\":\"UP\",\"analyticsQueueSize\":" + analyticsService.getQueueSize() + "}");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String extractIp(HttpServletRequest req) {
        String fwd = req.getHeader("X-Forwarded-For");
        return (fwd != null && !fwd.isBlank()) ? fwd.split(",")[0].trim() : req.getRemoteAddr();
    }

    /** One-way hash of IP for privacy-safe analytics storage */
    private String hashIp(String ip) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(ip.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            return sb.toString(); // 16-char hex prefix of SHA-256
        } catch (NoSuchAlgorithmException e) {
            return "unknown";
        }
    }
}
