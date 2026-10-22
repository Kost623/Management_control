package com.managementcontrol.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Зареєстрований клієнтський пристрій (ноутбук менеджера).
 * device_id — ідентифікатор, згенерований агентом при першому запуску.
 */
@Entity
@Table(name = "devices", indexes = {
        @Index(name = "idx_devices_status", columnList = "status"),
        @Index(name = "idx_devices_last_seen", columnList = "last_seen_at")
})
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false, unique = true, length = 128)
    private String deviceId;

    @Column(name = "hostname", nullable = false, length = 255)
    private String hostname;

    @Column(name = "os_name", length = 128)
    private String osName;

    @Column(name = "os_version", length = 128)
    private String osVersion;

    @Column(name = "agent_version", length = 64)
    private String agentVersion;

    @Column(name = "mac_address", length = 64)
    private String macAddress;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "os_user", length = 128)
    private String osUser;

    @Column(name = "assigned_manager", length = 128)
    private String assignedManager;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private DeviceStatus status = DeviceStatus.ACTIVE;

    @Column(name = "enrolled_at", nullable = false)
    private Instant enrolledAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "token_hash", nullable = false, length = 128)
    private String tokenHash;

    @Column(name = "token_expires_at")
    private Instant tokenExpiresAt;

    @Column(name = "tamper_protection", nullable = false)
    private boolean tamperProtection = true;

    @Column(name = "lock_reason", length = 512)
    private String lockReason;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "notes", length = 1024)
    private String notes;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getOsName() {
        return osName;
    }

    public void setOsName(String osName) {
        this.osName = osName;
    }

    public String getOsVersion() {
        return osVersion;
    }

    public void setOsVersion(String osVersion) {
        this.osVersion = osVersion;
    }

    public String getAgentVersion() {
        return agentVersion;
    }

    public void setAgentVersion(String agentVersion) {
        this.agentVersion = agentVersion;
    }

    public String getMacAddress() {
        return macAddress;
    }

    public void setMacAddress(String macAddress) {
        this.macAddress = macAddress;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getOsUser() {
        return osUser;
    }

    public void setOsUser(String osUser) {
        this.osUser = osUser;
    }

    public String getAssignedManager() {
        return assignedManager;
    }

    public void setAssignedManager(String assignedManager) {
        this.assignedManager = assignedManager;
    }

    public DeviceStatus getStatus() {
        return status;
    }

    public void setStatus(DeviceStatus status) {
        this.status = status;
    }

    public Instant getEnrolledAt() {
        return enrolledAt;
    }

    public void setEnrolledAt(Instant enrolledAt) {
        this.enrolledAt = enrolledAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public Instant getTokenExpiresAt() {
        return tokenExpiresAt;
    }

    public void setTokenExpiresAt(Instant tokenExpiresAt) {
        this.tokenExpiresAt = tokenExpiresAt;
    }

    public boolean isTamperProtection() {
        return tamperProtection;
    }

    public void setTamperProtection(boolean tamperProtection) {
        this.tamperProtection = tamperProtection;
    }

    public String getLockReason() {
        return lockReason;
    }

    public void setLockReason(String lockReason) {
        this.lockReason = lockReason;
    }

    public Instant getLockedAt() {
        return lockedAt;
    }

    public void setLockedAt(Instant lockedAt) {
        this.lockedAt = lockedAt;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
