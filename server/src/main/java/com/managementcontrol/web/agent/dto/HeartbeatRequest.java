package com.managementcontrol.web.agent.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** Heartbeat агента — підтвердження, що пристрій живий. */
public record HeartbeatRequest(
        @Size(max = 64) String agentVersion,
        @Size(max = 64) String ipAddress,
        @Min(0) @Max(100) Integer batteryPercent,
        Boolean charging,
        @Size(max = 64) String loggedInUser,
        @Size(max = 512) String tamperStatus
) {
}
