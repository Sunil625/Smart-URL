package com.smarturl.repository;

import com.smarturl.entity.AnalyticsEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface AnalyticsEventRepository extends JpaRepository<AnalyticsEvent, Long> {
    List<AnalyticsEvent> findByShortCode(String shortCode);

    @Query("SELECT COUNT(a) FROM AnalyticsEvent a WHERE a.shortCode = :shortCode AND a.clickedAt >= :since")
    Long countClicksSince(String shortCode, LocalDateTime since);

    @Query("SELECT a.referrer, COUNT(a) as cnt FROM AnalyticsEvent a WHERE a.shortCode = :shortCode GROUP BY a.referrer ORDER BY cnt DESC")
    List<Object[]> findTopReferrers(String shortCode);
}
