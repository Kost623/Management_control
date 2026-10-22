package com.managementcontrol.service;

import com.managementcontrol.common.BadRequestException;
import com.managementcontrol.common.NotFoundException;
import com.managementcontrol.config.ManagementControlProperties;
import com.managementcontrol.domain.Device;
import com.managementcontrol.domain.DeviceStatus;
import com.managementcontrol.domain.EnrollmentCode;
import com.managementcontrol.repository.DeviceRepository;
import com.managementcontrol.repository.EnrollmentCodeRepository;
import com.managementcontrol.security.TokenService;
import com.managementcontrol.web.agent.dto.EnrollRequest;
import com.managementcontrol.web.agent.dto.EnrollResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Реєстрація пристроїв.
 * Адміністратор генерує одноразовий код, агент обмінює його на device-токен.
 */
@Service
public class EnrollmentService {

    private static final Logger log = LoggerFactory.getLogger(EnrollmentService.class);

    /** Коди без схемних символів, щоб їх легко було продиктувати. */
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final EnrollmentCodeRepository enrollmentCodeRepository;
    private final DeviceRepository deviceRepository;
    private final ManagementControlProperties properties;
    private final AuditService auditService;

    public EnrollmentService(EnrollmentCodeRepository enrollmentCodeRepository,
                             DeviceRepository deviceRepository,
                             ManagementControlProperties properties,
                             AuditService auditService) {
        this.enrollmentCodeRepository = enrollmentCodeRepository;
        this.deviceRepository = deviceRepository;
        this.properties = properties;
        this.auditService = auditService;
    }

    @Transactional
    public EnrollmentCode createCode(String label, String assignedTo, String boundDeviceId, Duration ttl) {
        EnrollmentCode code = new EnrollmentCode();
        code.setCode(generateCode());
        code.setLabel(label);
        code.setAssignedTo(assignedTo);
        code.setBoundDeviceId(boundDeviceId);
        code.setCreatedAt(Instant.now());
        code.setExpiresAt(Instant.now().plus(ttl == null ? Duration.ofHours(24) : ttl));
        code.setActive(true);
        return enrollmentCodeRepository.save(code);
    }

    @Transactional
    public EnrollResponse enroll(EnrollRequest request) {
        EnrollmentCode enrollmentCode = enrollmentCodeRepository.findByCode(request.code())
                .orElseThrow(() -> new BadRequestException("Невірний код реєстрації"));

        if (!enrollmentCode.isActive()) {
            throw new BadRequestException("Код реєстрації вже використано");
        }
        if (enrollmentCode.getExpiresAt() != null && enrollmentCode.getExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("Термін дії коду реєстрації минув");
        }
        if (enrollmentCode.getBoundDeviceId() != null
                && !enrollmentCode.getBoundDeviceId().equals(request.deviceId())) {
            throw new BadRequestException("Код призначений для іншого пристрою");
        }

        Device device = deviceRepository.findByDeviceId(request.deviceId()).orElseGet(Device::new);
        boolean reEnrollment = device.getId() != null;

        String rawToken = TokenService.generateDeviceToken();

        device.setDeviceId(request.deviceId());
        device.setHostname(request.hostname());
        device.setOsName(request.osName());
        device.setOsVersion(request.osVersion());
        device.setAgentVersion(request.agentVersion());
        device.setMacAddress(request.macAddress());
        device.setIpAddress(request.ipAddress());
        device.setOsUser(request.osUser());
        device.setTokenHash(TokenService.hash(rawToken));
        device.setTokenExpiresAt(Instant.now().plus(properties.getDeviceTokenTtl()));
        device.setLastSeenAt(Instant.now());
        if (device.getStatus() == null) {
            device.setStatus(DeviceStatus.ACTIVE);
        }
        if (device.getEnrolledAt() == null) {
            device.setEnrolledAt(Instant.now());
        }
        if (enrollmentCode.getAssignedTo() != null && device.getAssignedManager() == null) {
            device.setAssignedManager(enrollmentCode.getAssignedTo());
        }
        if (device.getStatus() == DeviceStatus.LOCKED) {
            // пристрій заблокований адміністратором — реєстрація не знімає блокування
            log.info("Пристрій {} повторно зареєстровано, але лишається заблокованим", request.deviceId());
        }
        device = deviceRepository.save(device);

        enrollmentCode.setActive(false);
        enrollmentCode.setUsedAt(Instant.now());
        enrollmentCode.setUsedByDeviceId(request.deviceId());
        enrollmentCodeRepository.save(enrollmentCode);

        auditService.record("agent:" + request.deviceId(), "DEVICE_ENROLLED", "DEVICE",
                String.valueOf(device.getId()), reEnrollment ? "повторна реєстрація" : "первинна реєстрація");

        return new EnrollResponse(
                device.getId(),
                device.getDeviceId(),
                rawToken,
                device.getTokenExpiresAt(),
                Instant.now(),
                properties.getHeartbeatInterval().toSeconds(),
                properties.getCommandPollTimeout().toSeconds(),
                properties.getStorage().getMinScreenshotsPerDay(),
                properties.getStorage().getMaxScreenshotsPerDay());
    }

    @Transactional(readOnly = true)
    public Device requireDevice(Long deviceId) {
        return deviceRepository.findById(deviceId).orElseThrow(() -> new NotFoundException("Device", deviceId));
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            if (i > 0 && i % 4 == 0) {
                sb.append('-');
            }
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
