package com.managementcontrol.web.admin.dto;

import jakarta.validation.constraints.Size;

import java.time.Duration;

/** Запит на створення коду реєстрації. */
public record CreateEnrollmentCodeRequest(
        @Size(max = 128) String label,
        @Size(max = 128) String assignedTo,
        @Size(max = 128) String boundDeviceId,
        Duration ttl
) {
}
