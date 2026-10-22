package com.managementcontrol.web.admin.dto;

import com.managementcontrol.domain.RemoteCommandType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Duration;

/** Запит на створення команди пристрою. */
public record CreateCommandRequest(
        @NotNull RemoteCommandType type,
        @Size(max = 4096) String payload,
        Duration ttl
) {
}
