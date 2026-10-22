package com.managementcontrol.service;

import com.managementcontrol.common.BadRequestException;
import com.managementcontrol.common.NotFoundException;
import com.managementcontrol.config.ManagementControlProperties;
import com.managementcontrol.domain.Device;
import com.managementcontrol.domain.DeviceStatus;
import com.managementcontrol.domain.RemoteCommand;
import com.managementcontrol.domain.RemoteCommandStatus;
import com.managementcontrol.domain.RemoteCommandType;
import com.managementcontrol.repository.DeviceRepository;
import com.managementcontrol.repository.RemoteCommandRepository;
import com.managementcontrol.web.agent.dto.AgentCommandDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Черга команд для пристроїв.
 * Агент забирає команди long-poll запитом; сервер блокує запит, поки команда не з'явиться.
 */
@Service
public class CommandService {

    private static final Logger log = LoggerFactory.getLogger(CommandService.class);

    private final Set<RemoteCommandStatus> DELIVERABLE = EnumSet.of(RemoteCommandStatus.PENDING);

    private final RemoteCommandRepository commandRepository;
    private final DeviceRepository deviceRepository;
    private final ManagementControlProperties properties;
    private final AuditService auditService;

    /** Умова для очікування нових команд. */
    private final Object signal = new Object();

    public CommandService(RemoteCommandRepository commandRepository,
                          DeviceRepository deviceRepository,
                          ManagementControlProperties properties,
                          AuditService auditService) {
        this.commandRepository = commandRepository;
        this.deviceRepository = deviceRepository;
        this.properties = properties;
        this.auditService = auditService;
    }

    @Transactional
    public RemoteCommand create(Long deviceId, RemoteCommandType type, String payload, String createdBy, Duration ttl) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new NotFoundException("Device", deviceId));

        RemoteCommand command = new RemoteCommand();
        command.setDeviceId(deviceId);
        command.setType(type);
        command.setStatus(RemoteCommandStatus.PENDING);
        command.setPayload(payload);
        command.setCreatedAt(Instant.now());
        command.setExpiresAt(Instant.now().plus(ttl == null ? Duration.ofHours(1) : ttl));
        command.setCreatedBy(createdBy);
        command = commandRepository.save(command);

        if (type == RemoteCommandType.LOCK) {
            device.setStatus(DeviceStatus.LOCKED);
            device.setLockedAt(Instant.now());
            device.setLockReason(payload);
            deviceRepository.save(device);
        } else if (type == RemoteCommandType.UNLOCK) {
            device.setStatus(DeviceStatus.ACTIVE);
            device.setLockedAt(null);
            device.setLockReason(null);
            deviceRepository.save(device);
        }

        auditService.record(createdBy, "COMMAND_" + type.name(), "DEVICE", String.valueOf(deviceId), payload);
        notifyWaiters();
        return command;
    }

    /**
     * Long-poll: чекаємо на команду до сплину command-poll-timeout.
     */
    public List<AgentCommandDto> poll(Long deviceId, Duration timeout) {
        Duration effective = timeout == null ? properties.getCommandPollTimeout() : timeout;
        long deadline = System.currentTimeMillis() + effective.toMillis();

        synchronized (signal) {
            while (true) {
                List<RemoteCommand> pending = commandRepository
                        .findByDeviceIdAndStatusInOrderByCreatedAtAsc(deviceId, DELIVERABLE);
                if (!pending.isEmpty()) {
                    Instant now = Instant.now();
                    pending.forEach(command -> {
                        command.setStatus(RemoteCommandStatus.DELIVERED);
                        command.setDeliveredAt(now);
                    });
                    commandRepository.saveAll(pending);
                    return pending.stream().map(CommandService::toDto).toList();
                }
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    return List.of();
                }
                try {
                    signal.wait(Math.min(remaining, 2000));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return List.of();
                }
            }
        }
    }

    @Transactional
    public void acknowledge(Long deviceId, Long commandId, String status, String result) {
        RemoteCommand command = commandRepository.findById(commandId)
                .orElseThrow(() -> new NotFoundException("Команду", commandId));
        if (!command.getDeviceId().equals(deviceId)) {
            throw new BadRequestException("Команда належить іншому пристрою");
        }
        command.setStatus("EXECUTED".equals(status) ? RemoteCommandStatus.EXECUTED : RemoteCommandStatus.FAILED);
        command.setExecutedAt(Instant.now());
        command.setResult(result);
        commandRepository.save(command);
    }

    @Transactional
    public int expireOutdated() {
        List<RemoteCommand> outdated = commandRepository
                .findByStatusAndExpiresAtBefore(RemoteCommandStatus.PENDING, Instant.now());
        outdated.forEach(command -> command.setStatus(RemoteCommandStatus.EXPIRED));
        commandRepository.saveAll(outdated);
        return outdated.size();
    }

    private void notifyWaiters() {
        synchronized (signal) {
            signal.notifyAll();
        }
    }

    private static AgentCommandDto toDto(RemoteCommand command) {
        return new AgentCommandDto(command.getId(), command.getType().name(), command.getPayload(),
                command.getCreatedAt(), command.getExpiresAt());
    }
}
