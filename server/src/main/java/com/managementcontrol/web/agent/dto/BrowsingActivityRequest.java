package com.managementcontrol.web.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Один інтервал активного відвідування сайту. */
public record BrowsingActivityRequest(
        @Size(max = 2048) String url,
        @NotBlank @Size(max = 255) String domain,
        @Size(max = 128) String browserName,
        @Size(max = 512) String tabTitle,
        @NotNull Instant startedAt,
        @NotNull Instant endedAt,
        @PositiveOrZero long durationSeconds,
        boolean idle
) {
}
