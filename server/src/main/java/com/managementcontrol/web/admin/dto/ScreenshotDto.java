package com.managementcontrol.web.admin.dto;

import java.time.Instant;

/** Скриншот у списку (без самого зображення). */
public record ScreenshotDto(
        Long id,
        Long deviceId,
        String fileName,
        long sizeBytes,
        Instant takenAt,
        String triggerType,
        Integer width,
        Integer height,
        String note,
        String downloadUrl
) {
}
