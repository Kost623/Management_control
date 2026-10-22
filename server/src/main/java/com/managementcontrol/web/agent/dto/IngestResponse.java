package com.managementcontrol.web.agent.dto;

import java.time.Instant;

/** Відповідь на прийом пачки даних. */
public record IngestResponse(
        int accepted,
        Instant serverTime
) {
    public static IngestResponse of(int accepted) {
        return new IngestResponse(accepted, Instant.now());
    }
}
