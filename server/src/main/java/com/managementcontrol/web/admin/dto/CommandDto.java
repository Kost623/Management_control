package com.managementcontrol.web.admin.dto;

import java.time.Instant;

/** Команда пристрою у списку. */
public record CommandDto(
        Long id,
        Long deviceId,
        String type,
        String status,
        String payload,
        Instant createdAt,
        Instant deliveredAt,
        Instant executedAt,
        Instant expiresAt,
        String createdBy,
        String result
) {
}
