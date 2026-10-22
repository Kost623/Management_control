package com.managementcontrol.web.admin.dto;

import java.time.Instant;

/** Інтервал використання програми у списку. */
public record UsageSessionDto(
        Long id,
        Long deviceId,
        String processName,
        String windowTitle,
        Instant startedAt,
        Instant endedAt,
        long durationSeconds,
        boolean idle,
        boolean foreground
) {
}
