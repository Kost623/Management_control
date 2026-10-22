package com.managementcontrol.web.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Один інтервал використання програми. */
public record UsageSessionRequest(
        @NotBlank @Size(max = 255) String processName,
        @Size(max = 512) String windowTitle,
        @Size(max = 1024) String exePath,
        @NotNull Instant startedAt,
        @NotNull Instant endedAt,
        @PositiveOrZero long durationSeconds,
        boolean idle,
        boolean foreground
) {
}
