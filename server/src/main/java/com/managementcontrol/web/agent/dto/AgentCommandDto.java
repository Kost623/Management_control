package com.managementcontrol.web.agent.dto;

import java.time.Instant;

/** Команда, яку сервер надсилає агенту. */
public record AgentCommandDto(
        Long id,
        String type,
        String payload,
        Instant createdAt,
        Instant expiresAt
) {
}
