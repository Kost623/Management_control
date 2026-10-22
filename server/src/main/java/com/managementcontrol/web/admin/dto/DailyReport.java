package com.managementcontrol.web.admin.dto;

import java.time.Instant;

/** Короткий звіт за добу. */
public record DailyReport(
        Long deviceId,
        String deviceUid,
        String hostname,
        java.time.LocalDate date,
        long activeSeconds,
        long screenshotCount,
        java.util.List<TopItem> topApps,
        java.util.List<TopItem> topSites,
        Double lastLatitude,
        Double lastLongitude,
        Instant lastLocationAt
) {
}
