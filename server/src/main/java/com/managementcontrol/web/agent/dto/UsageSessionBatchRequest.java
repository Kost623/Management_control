package com.managementcontrol.web.agent.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Пакет інтервалів використання програм. */
public record UsageSessionBatchRequest(
        @NotEmpty List<@Valid UsageSessionRequest> sessions
) {
}
