package com.managementcontrol.web.agent.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Пакет подій запуску/завершення процесів. */
public record ProcessEventBatchRequest(
        @NotEmpty List<@Valid ProcessEventRequest> events
) {
}
