package com.managementcontrol.web.admin.dto;

import java.time.Instant;

/** Відвідування сайту у списку. */
public record BrowsingActivityDto(
        Long id,
        Long deviceId,
        String url,
        String domain,
        String browserName,
        String tabTitle,
        Instant startedAt,
        Instant endedAt,
        long durationSeconds,
        boolean idle
) {
}
