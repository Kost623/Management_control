package com.managementcontrol.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Одноразовий код для реєстрації пристрою.
 * Адміністратор генерує код і передає його менеджеру (наприклад, у месенджері).
 */
@Entity
@Table(name = "enrollment_codes")
public class EnrollmentCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 32)
    private String code;

    @Column(name = "label", length = 128)
    private String label;

    /** Кому призначений код (логін менеджера). */
    @Column(name = "assigned_to", length = 128)
    private String assignedTo;

    /** Якщо задано — код прив'язаний до конкретного device_id. */
    @Column(name = "bound_device_id", length = 128)
    private String boundDeviceId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "used_by_device_id", length = 128)
    private String usedByDeviceId;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(String assignedTo) {
        this.assignedTo = assignedTo;
    }

    public String getBoundDeviceId() {
        return boundDeviceId;
    }

    public void setBoundDeviceId(String boundDeviceId) {
        this.boundDeviceId = boundDeviceId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public void setUsedAt(Instant usedAt) {
        this.usedAt = usedAt;
    }

    public String getUsedByDeviceId() {
        return usedByDeviceId;
    }

    public void setUsedByDeviceId(String usedByDeviceId) {
        this.usedByDeviceId = usedByDeviceId;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
