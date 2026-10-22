package com.managementcontrol.web.admin.dto;

import com.managementcontrol.domain.DeviceStatus;

import java.time.Instant;

/** Представлення пристрою для панелі керування. */
public record DeviceDto(
        Long id,
        String deviceId,
        String hostname,
        String osName,
        String osVersion,
        String agentVersion,
        String ipAddress,
        String osUser,
        String assignedManager,
        DeviceStatus status,
        Instant enrolledAt,
        Instant lastSeenAt,
        boolean tamperProtection,
        String lockReason,
        Instant lockedAt,
        String notes
) {
    public static DeviceDto from(com.managementcontrol.domain.Device device) {
        return new DeviceDto(
                device.getId(),
                device.getDeviceId(),
                device.getHostname(),
                device.getOsName(),
                device.getOsVersion(),
                device.getAgentVersion(),
                device.getIpAddress(),
                device.getOsUser(),
                device.getAssignedManager(),
                device.getStatus(),
                device.getEnrolledAt(),
                device.getLastSeenAt(),
                device.isTamperProtection(),
                device.getLockReason(),
                device.getLockedAt(),
                device.getNotes());
    }
}
