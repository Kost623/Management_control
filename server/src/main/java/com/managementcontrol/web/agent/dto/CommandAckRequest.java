package com.managementcontrol.web.agent.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Підтвердження виконання команди агентом. */
public record CommandAckRequest(
        @NotNull Long commandId,
        @Pattern(regexp = "EXECUTED|FAILED", message = "Невідомий статус виконання") String status,
        @Size(max = 1024) String result
) {
}
