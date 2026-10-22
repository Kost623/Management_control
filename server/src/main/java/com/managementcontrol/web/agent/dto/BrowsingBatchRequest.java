package com.managementcontrol.web.agent.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Пакет активних відвідувань сайтів. */
public record BrowsingBatchRequest(
        @NotEmpty List<@Valid BrowsingActivityRequest> activities
) {
}
