package com.managementcontrol.web.admin.dto;

import java.time.Instant;

/** Код реєстрації пристрою. */
public record EnrollmentCodeDto(
        Long id,
        String code,
        String label,
        String assignedTo,
        String boundDeviceId,
        Instant createdAt,
        Instant expiresAt,
        Instant usedAt,
        String usedByDeviceId,
        boolean active
) {
}
