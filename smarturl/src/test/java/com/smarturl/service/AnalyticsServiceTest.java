package com.smarturl.service;

import com.smarturl.entity.AnalyticsEvent;
import com.smarturl.entity.Url;
import com.smarturl.entity.User;
import com.smarturl.repository.AnalyticsEventRepository;
import com.smarturl.repository.UrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnalyticsService Tests")
class AnalyticsServiceTest {

    @Mock private AnalyticsEventRepository analyticsRepo;
    @Mock private UrlRepository urlRepository;

    @InjectMocks private AnalyticsService analyticsService;

    @Test
    @DisplayName("trackClick enqueues event without blocking")
    void trackClickEnqueuesEvent() {
        // Should not throw even with null referrer
        assertThatNoException().isThrownBy(() ->
                analyticsService.trackClick("abc1234", "hash123", null, "Mozilla/5.0")
        );
        assertThat(analyticsService.getQueueSize()).isEqualTo(1);
    }

    @Test
    @DisplayName("queue size increments on each trackClick")
    void queueSizeIncrements() {
        for (int i = 0; i < 5; i++) {
            analyticsService.trackClick("code" + i, "hash", "https://google.com", "agent");
        }
        assertThat(analyticsService.getQueueSize()).isEqualTo(5);
    }

    @Test
    @DisplayName("flushAnalytics drains queue and saves events")
    void flushAnalyticsSavesEvents() {
        analyticsService.trackClick("testcode", "hash", "ref", "ua");

        Url mockUrl = Url.builder().shortCode("testcode").longUrl("https://example.com")
                .clickCount(1L).build();
        when(urlRepository.findByShortCode("testcode")).thenReturn(Optional.of(mockUrl));
        when(analyticsRepo.countClicksSince(any(), any())).thenReturn(1L);
        when(analyticsRepo.findTopReferrers(any())).thenReturn(java.util.List.of());

        analyticsService.flushAnalytics();

        verify(analyticsRepo, times(1)).saveAll(anyList());
        assertThat(analyticsService.getQueueSize()).isEqualTo(0);
    }
}
