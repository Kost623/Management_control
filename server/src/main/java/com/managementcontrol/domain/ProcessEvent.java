package com.managementcontrol.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/** Подія запуску / завершення процесу на пристрої. */
@Entity
@Table(name = "process_events", indexes = {
        @Index(name = "idx_process_device_time", columnList = "device_id, occurred_at"),
        @Index(name = "idx_process_name", columnList = "process_name")
})
public class ProcessEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    @Column(name = "process_name", nullable = false, length = 255)
    private String processName;

    @Column(name = "exe_path", length = 1024)
    private String exePath;

    @Column(name = "command_line", length = 2048)
    private String commandLine;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    /** STARTED або EXITED. */
    @Column(name = "action", nullable = false, length = 32)
    private String action;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(Long deviceId) {
        this.deviceId = deviceId;
    }

    public String getProcessName() {
        return processName;
    }

    public void setProcessName(String processName) {
        this.processName = processName;
    }

    public String getExePath() {
        return exePath;
    }

    public void setExePath(String exePath) {
        this.exePath = exePath;
    }

    public String getCommandLine() {
        return commandLine;
    }

    public void setCommandLine(String commandLine) {
        this.commandLine = commandLine;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }
}
