package com.managementcontrol.service;

import com.managementcontrol.domain.BrowsingActivity;
import com.managementcontrol.domain.Device;
import com.managementcontrol.domain.LocationPing;
import com.managementcontrol.domain.ProcessEvent;
import com.managementcontrol.domain.UsageSession;
import com.managementcontrol.repository.BrowsingActivityRepository;
import com.managementcontrol.repository.DeviceRepository;
import com.managementcontrol.repository.LocationPingRepository;
import com.managementcontrol.repository.ProcessEventRepository;
import com.managementcontrol.repository.UsageSessionRepository;
import com.managementcontrol.web.agent.dto.BrowsingActivityRequest;
import com.managementcontrol.web.agent.dto.LocationRequest;
import com.managementcontrol.web.agent.dto.ProcessEventRequest;
import com.managementcontrol.web.agent.dto.UsageSessionRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Прийом телеметрії від агента.
 * Дані зберігаються лише на сервері — на пристрої вони не залишаються.
 */
@Service
public class TelemetryService {

    private final DeviceRepository deviceRepository;
    private final UsageSessionRepository usageSessionRepository;
    private final BrowsingActivityRepository browsingActivityRepository;
    private final ProcessEventRepository processEventRepository;
    private final LocationPingRepository locationPingRepository;

    public TelemetryService(DeviceRepository deviceRepository,
                            UsageSessionRepository usageSessionRepository,
                            BrowsingActivityRepository browsingActivityRepository,
                            ProcessEventRepository processEventRepository,
                            LocationPingRepository locationPingRepository) {
        this.deviceRepository = deviceRepository;
        this.usageSessionRepository = usageSessionRepository;
        this.browsingActivityRepository = browsingActivityRepository;
        this.processEventRepository = processEventRepository;
        this.locationPingRepository = locationPingRepository;
    }

    @Transactional
    public int ingestUsageSessions(Device device, List<UsageSessionRequest> requests) {
        List<UsageSession> entities = requests.stream()
                .map(request -> toEntity(device.getId(), request))
                .toList();
        usageSessionRepository.saveAll(entities);
        return entities.size();
    }

    @Transactional
    public int ingestBrowsing(Device device, List<BrowsingActivityRequest> requests) {
        List<BrowsingActivity> entities = requests.stream()
                .map(request -> toEntity(device.getId(), request))
                .toList();
        browsingActivityRepository.saveAll(entities);
        return entities.size();
    }

    @Transactional
    public int ingestProcessEvents(Device device, List<ProcessEventRequest> requests) {
        List<ProcessEvent> entities = requests.stream()
                .map(request -> toEntity(device.getId(), request))
                .toList();
        processEventRepository.saveAll(entities);
        return entities.size();
    }

    @Transactional
    public LocationPing ingestLocation(Device device, LocationRequest request) {
        LocationPing ping = new LocationPing();
        ping.setDeviceId(device.getId());
        ping.setLatitude(request.latitude());
        ping.setLongitude(request.longitude());
        ping.setAccuracy(request.accuracy());
        ping.setAltitude(request.altitude());
        ping.setSpeed(request.speed());
        ping.setCapturedAt(request.capturedAt());
        ping.setSource(request.source() == null ? "GPS" : request.source());
        return locationPingRepository.save(ping);
    }

    @Transactional
    public Device touch(Device device, String agentVersion, String ipAddress) {
        if (agentVersion != null && !agentVersion.isBlank()) {
            device.setAgentVersion(agentVersion);
        }
        if (ipAddress != null && !ipAddress.isBlank()) {
            device.setIpAddress(ipAddress);
        }
        device.setLastSeenAt(Instant.now());
        return deviceRepository.save(device);
    }

    private UsageSession toEntity(Long deviceId, UsageSessionRequest request) {
        UsageSession session = new UsageSession();
        session.setDeviceId(deviceId);
        session.setProcessName(request.processName());
        session.setWindowTitle(request.windowTitle());
        session.setExePath(request.exePath());
        session.setStartedAt(request.startedAt());
        session.setEndedAt(request.endedAt());
        session.setDurationSeconds(request.durationSeconds());
        session.setIdle(request.idle());
        session.setForeground(request.foreground());
        return session;
    }

    private BrowsingActivity toEntity(Long deviceId, BrowsingActivityRequest request) {
        BrowsingActivity activity = new BrowsingActivity();
        activity.setDeviceId(deviceId);
        activity.setUrl(request.url());
        activity.setDomain(request.domain());
        activity.setBrowserName(request.browserName());
        activity.setTabTitle(request.tabTitle());
        activity.setStartedAt(request.startedAt());
        activity.setEndedAt(request.endedAt());
        activity.setDurationSeconds(request.durationSeconds());
        activity.setIdle(request.idle());
        return activity;
    }

    private ProcessEvent toEntity(Long deviceId, ProcessEventRequest request) {
        ProcessEvent event = new ProcessEvent();
        event.setDeviceId(deviceId);
        event.setProcessName(request.processName());
        event.setExePath(request.exePath());
        event.setCommandLine(request.commandLine());
        event.setOccurredAt(request.occurredAt());
        event.setAction(request.action());
        return event;
    }
}
