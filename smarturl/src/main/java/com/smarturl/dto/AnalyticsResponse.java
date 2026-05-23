package com.smarturl.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class AnalyticsResponse {
    private String shortCode;
    private String longUrl;
    private Long totalClicks;
    private Long clicksLast24h;
    private Long clicksLast7d;
    private Map<String, Long> topReferrers;
}
