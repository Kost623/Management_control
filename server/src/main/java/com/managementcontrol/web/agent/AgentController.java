package com.managementcontrol.web.agent;

import com.managementcontrol.config.ManagementControlProperties;
import com.managementcontrol.domain.Device;
import com.managementcontrol.service.CommandService;
import com.managementcontrol.service.EnrollmentService;
import com.managementcontrol.service.TelemetryService;
import com.managementcontrol.web.agent.dto.AgentCommandDto;
import com.managementcontrol.web.agent.dto.BrowsingBatchRequest;
import com.managementcontrol.web.agent.dto.CommandAckRequest;
import com.managementcontrol.web.agent.dto.EnrollRequest;
import com.managementcontrol.web.agent.dto.EnrollResponse;
import com.managementcontrol.web.agent.dto.HeartbeatRequest;
import com.managementcontrol.web.agent.dto.IngestResponse;
import com.managementcontrol.web.agent.dto.LocationRequest;
import com.managementcontrol.web.agent.dto.ProcessEventBatchRequest;
import com.managementcontrol.web.agent.dto.UsageSessionBatchRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

/**
 * API для агента на клієнті.
 * Усі запити автентифікуються device-токеном (mc_dev_...).
 */
@RestController
@RequestMapping("/api/v1/agent")
public class AgentController {

    private final EnrollmentService enrollmentService;
    private final TelemetryService telemetryService;
    private final CommandService commandService;
    private final CurrentDeviceResolver currentDevice;
    private final ManagementControlProperties properties;

    public AgentController(EnrollmentService enrollmentService,
                           TelemetryService telemetryService,
                           CommandService commandService,
                           CurrentDeviceResolver currentDevice,
                           ManagementControlProperties properties) {
        this.enrollmentService = enrollmentService;
        this.telemetryService = telemetryService;
        this.commandService = commandService;
        this.currentDevice = currentDevice;
        this.properties = properties;
    }

    /** Реєстрація пристрою: обмін коду на токен. */
    @PostMapping("/enroll")
    public ResponseEntity<EnrollResponse> enroll(@Valid @RequestBody EnrollRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(enrollmentService.enroll(request));
    }

    /** Heartbeat — підтвердження, що агент живий. */
    @PostMapping("/heartbeat")
    public ResponseEntity<IngestResponse> heartbeat(@Valid @RequestBody HeartbeatRequest request) {
        Device device = currentDevice.require();
        telemetryService.touch(device, request.agentVersion(), request.ipAddress());
        return ResponseEntity.ok(IngestResponse.of(1));
    }

    /** Пакет інтервалів використання програм. */
    @PostMapping("/usage-sessions")
    public ResponseEntity<IngestResponse> usageSessions(@Valid @RequestBody UsageSessionBatchRequest request) {
        Device device = currentDevice.require();
        int accepted = telemetryService.ingestUsageSessions(device, request.sessions());
        telemetryService.touch(device, null, null);
        return ResponseEntity.ok(IngestResponse.of(accepted));
    }

    /** Пакет активних відвідувань сайтів. */
    @PostMapping("/browsing")
    public ResponseEntity<IngestResponse> browsing(@Valid @RequestBody BrowsingBatchRequest request) {
        Device device = currentDevice.require();
        int accepted = telemetryService.ingestBrowsing(device, request.activities());
        telemetryService.touch(device, null, null);
        return ResponseEntity.ok(IngestResponse.of(accepted));
    }

    /** Пакет подій запуску/завершення процесів. */
    @PostMapping("/processes")
    public ResponseEntity<IngestResponse> processes(@Valid @RequestBody ProcessEventBatchRequest request) {
        Device device = currentDevice.require();
        int accepted = telemetryService.ingestProcessEvents(device, request.events());
        telemetryService.touch(device, null, null);
        return ResponseEntity.ok(IngestResponse.of(accepted));
    }

    /** Поточні координати пристрою. */
    @PostMapping("/location")
    public ResponseEntity<IngestResponse> location(@Valid @RequestBody LocationRequest request) {
        Device device = currentDevice.require();
        telemetryService.ingestLocation(device, request);
        telemetryService.touch(device, null, null);
        return ResponseEntity.ok(IngestResponse.of(1));
    }

    /** Long-poll: очікування команд від сервера. */
    @GetMapping("/commands")
    public List<AgentCommandDto> commands(@RequestParam(required = false) Long timeoutSeconds) {
        Device device = currentDevice.require();
        Duration timeout = timeoutSeconds == null
                ? properties.getCommandPollTimeout()
                : Duration.ofSeconds(Math.min(Math.max(timeoutSeconds, 1), 120));
        return commandService.poll(device.getId(), timeout);
    }

    /** Підтвердження виконання команди. */
    @PostMapping("/commands/{commandId}/ack")
    public ResponseEntity<Void> ack(@PathVariable Long commandId,
                                    @Valid @RequestBody CommandAckRequest request) {
        Device device = currentDevice.require();
        if (!request.commandId().equals(commandId)) {
            throw new com.managementcontrol.common.BadRequestException("commandId у запиті та URL не збігаються");
        }
        commandService.acknowledge(device.getId(), commandId, request.status(), request.result());
        return ResponseEntity.noContent().build();
    }
}
