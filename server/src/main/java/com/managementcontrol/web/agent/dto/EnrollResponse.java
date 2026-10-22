package com.managementcontrol.web.agent.dto;

import java.time.Instant;

/**
 * Відповідь на реєстрацію. Токен повертається один раз — агент зберігає його
 * в захищеному сховищі операційної системи.
 */
public record EnrollResponse(
        Long deviceDbId,
        String deviceId,
        String token,
        Instant tokenExpiresAt,
        Instant serverTime,
        long heartbeatIntervalSeconds,
        long commandPollTimeoutSeconds,
        int minScreenshotsPerDay,
        int maxScreenshotsPerDay
) {
}
