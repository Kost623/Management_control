package com.managementcontrol.web.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Запит на реєстрацію пристрою агентом. */
public record EnrollRequest(
        @NotBlank @Size(max = 32) String code,
        @NotBlank @Size(max = 128) String deviceId,
        @NotBlank @Size(max = 255) String hostname,
        @Size(max = 128) String osName,
        @Size(max = 128) String osVersion,
        @Size(max = 64) String agentVersion,
        @Size(max = 64) String macAddress,
        @Size(max = 64) String ipAddress,
        @Size(max = 128) String osUser
) {
}
