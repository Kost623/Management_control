package com.managementcontrol.web.agent.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Координати пристрою. */
public record LocationRequest(
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @DecimalMin("0.0") Double accuracy,
        Double altitude,
        Double speed,
        @NotNull Instant capturedAt,
        @Pattern(regexp = "GPS|WIFI|CELL|MANUAL", message = "Невідомий тип джерела локації") String source
) {
}
