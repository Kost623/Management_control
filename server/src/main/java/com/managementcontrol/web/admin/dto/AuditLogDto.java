package com.managementcontrol.web.admin.dto;

import java.time.Instant;

/** Запис журналу аудиту. */
public record AuditLogDto(
        Long id,
        String actor,
        String action,
        String targetType,
        String targetId,
        String details,
        Instant createdAt
) {
}
