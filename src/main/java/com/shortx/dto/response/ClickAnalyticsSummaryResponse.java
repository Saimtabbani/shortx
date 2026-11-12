package com.shortx.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class ClickAnalyticsSummaryResponse {
    private String shortCode;
    private long totalClicks;
    private List<BreakdownItem> byCountry;
    private List<BreakdownItem> byDevice;
    private List<BreakdownItem> byBrowser;
    private List<BreakdownItem> byOs;
    private List<DailyClickCountItem> dailyTrend;
}
