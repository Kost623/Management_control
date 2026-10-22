package com.managementcontrol.service;

import com.managementcontrol.common.NotFoundException;
import com.managementcontrol.config.ManagementControlProperties;
import com.managementcontrol.domain.Device;
import com.managementcontrol.domain.DeviceStatus;
import com.managementcontrol.domain.RemoteCommandType;
import com.managementcontrol.repository.DeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Керування пристроями. */
@Service
public class DeviceService {

    private static final Logger log = LoggerFactory.getLogger(DeviceService.class);

    private final DeviceRepository deviceRepository;
    private final CommandService commandService;
    private final ManagementControlProperties properties;

    public DeviceService(DeviceRepository deviceRepository,
                         CommandService commandService,
                         ManagementControlProperties properties) {
        this.deviceRepository = deviceRepository;
        this.commandService = commandService;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public Page<Device> list(DeviceStatus status, Pageable pageable) {
        if (status == null) {
            return deviceRepository.findAllByOrderByIdDesc(pageable);
        }
        return deviceRepository.findByStatus(status, pageable);
    }

    @Transactional(readOnly = true)
    public Device get(Long id) {
        return deviceRepository.findById(id).orElseThrow(() -> new NotFoundException("Device", id));
    }

    @Transactional
    public Device lock(Long id, String reason, String actor) {
        Device device = get(id);
        device.setStatus(DeviceStatus.LOCKED);
        device.setLockedAt(Instant.now());
        device.setLockReason(reason);
        deviceRepository.save(device);
        commandService.create(id, RemoteCommandType.LOCK, reason, actor, Duration.ofMinutes(10));
        log.info("Пристрій {} заблоковано: {}", id, reason);
        return device;
    }

    @Transactional
    public Device unlock(Long id, String actor) {
        Device device = get(id);
        device.setStatus(DeviceStatus.ACTIVE);
        device.setLockedAt(null);
        device.setLockReason(null);
        deviceRepository.save(device);
        commandService.create(id, RemoteCommandType.UNLOCK, null, actor, Duration.ofMinutes(10));
        log.info("Пристрій {} розблоковано", id);
        return device;
    }

    @Transactional
    public Device block(Long id, String reason, String actor) {
        Device device = get(id);
        device.setStatus(DeviceStatus.BLOCKED);
        device.setNotes(reason);
        deviceRepository.save(device);
        log.info("Пристрій {} заблоковано назавжди: {}", id, reason);
        return device;
    }

    /**
     * Періодично переводить пристрої без heartbeat у статус OFFLINE.
     */
    @Scheduled(fixedDelayString = "PT1M")
    @Transactional
    public void markStaleDevicesOffline() {
        Instant threshold = Instant.now().minus(properties.getOfflineThreshold());
        List<Device> stale = deviceRepository.findStale(threshold, Pageable.unpaged()).getContent();
        for (Device device : stale) {
            if (device.getStatus() == DeviceStatus.ACTIVE) {
                device.setStatus(DeviceStatus.OFFLINE);
                deviceRepository.save(device);
                log.info("Пристрій {} позначено як OFFLINE (останній heartbeat: {})",
                        device.getDeviceId(), device.getLastSeenAt());
            }
        }
    }
}
