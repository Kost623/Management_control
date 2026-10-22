package com.managementcontrol.web.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Подія запуску або завершення процесу. */
public record ProcessEventRequest(
        @NotBlank @Size(max = 255) String processName,
        @Size(max = 1024) String exePath,
        @Size(max = 2048) String commandLine,
        @NotNull Instant occurredAt,
        @Pattern(regexp = "STARTED|EXITED", message = "action має бути STARTED або EXITED") String action
) {
}
