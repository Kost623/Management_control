package com.managementcontrol.service;

import com.managementcontrol.config.ManagementControlProperties;
import com.managementcontrol.domain.AuditLog;
import com.managementcontrol.domain.Device;
import com.managementcontrol.domain.DeviceStatus;
import com.managementcontrol.domain.EnrollmentCode;
import com.managementcontrol.domain.RemoteCommandType;
import com.managementcontrol.repository.AuditLogRepository;
import com.managementcontrol.repository.DeviceRepository;
import com.managementcontrol.repository.EnrollmentCodeRepository;
import com.managementcontrol.repository.RemoteCommandRepository;
import com.managementcontrol.web.agent.dto.EnrollRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mocking: ізоляція сервісів від реальних репозиторіїв/залежностей.
 * Перевіряється не лише результат, а й сама взаємодія — чи викликано
 * потрібний метод залежності, скільки разів і з якими аргументами.
 */
@ExtendWith(MockitoExtension.class)
class MockingInteractionTest {

    // ---------- 1. DeviceService.lock() ----------

    @Test
    void lock_ShouldSaveDeviceWithLockedStatus_AndCreateLockCommand() {
        DeviceRepository deviceRepository = org.mockito.Mockito.mock(DeviceRepository.class);
        CommandService commandService = org.mockito.Mockito.mock(CommandService.class);
        ManagementControlProperties properties = new ManagementControlProperties();

        DeviceService deviceService = new DeviceService(deviceRepository, commandService, properties);

        Device device = new Device();
        device.setId(1L);
        device.setStatus(DeviceStatus.ACTIVE);
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        when(deviceRepository.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Device result = deviceService.lock(1L, "Підозріла активність", "admin");

        assertThat(result.getStatus()).isEqualTo(DeviceStatus.LOCKED);

        ArgumentCaptor<Device> deviceCaptor = ArgumentCaptor.forClass(Device.class);
        verify(deviceRepository, times(1)).save(deviceCaptor.capture());
        assertThat(deviceCaptor.getValue().getStatus()).isEqualTo(DeviceStatus.LOCKED);
        assertThat(deviceCaptor.getValue().getLockReason()).isEqualTo("Підозріла активність");

        verify(commandService, times(1))
                .create(eq(1L), eq(RemoteCommandType.LOCK), eq("Підозріла активність"),
                        eq("admin"), eq(Duration.ofMinutes(10)));
    }

    // ---------- 2. DeviceService.unlock() ----------

    @Test
    void unlock_ShouldClearLockFields_AndCreateUnlockCommand() {
        DeviceRepository deviceRepository = org.mockito.Mockito.mock(DeviceRepository.class);
        CommandService commandService = org.mockito.Mockito.mock(CommandService.class);
        ManagementControlProperties properties = new ManagementControlProperties();

        DeviceService deviceService = new DeviceService(deviceRepository, commandService, properties);

        Device device = new Device();
        device.setId(2L);
        device.setStatus(DeviceStatus.LOCKED);
        device.setLockedAt(Instant.now());
        device.setLockReason("Раніше заблоковано");
        when(deviceRepository.findById(2L)).thenReturn(Optional.of(device));
        when(deviceRepository.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Device result = deviceService.unlock(2L, "admin");

        assertThat(result.getStatus()).isEqualTo(DeviceStatus.ACTIVE);
        assertThat(result.getLockedAt()).isNull();
        assertThat(result.getLockReason()).isNull();

        verify(commandService, times(1))
                .create(eq(2L), eq(RemoteCommandType.UNLOCK), eq(null), eq("admin"), eq(Duration.ofMinutes(10)));
    }

    // ---------- 4. DeviceService.list(status=null) ----------

    @Test
    void list_ShouldCallFindAllOrderedById_AndNeverCallFindByStatus_WhenStatusFilterIsNull() {
        DeviceRepository deviceRepository = org.mockito.Mockito.mock(DeviceRepository.class);
        CommandService commandService = org.mockito.Mockito.mock(CommandService.class);
        ManagementControlProperties properties = new ManagementControlProperties();

        DeviceService deviceService = new DeviceService(deviceRepository, commandService, properties);

        org.springframework.data.domain.Pageable pageable =
                org.springframework.data.domain.PageRequest.of(0, 10);
        when(deviceRepository.findAllByOrderByIdDesc(pageable))
                .thenReturn(org.springframework.data.domain.Page.empty());

        deviceService.list(null, pageable);

        verify(deviceRepository, times(1)).findAllByOrderByIdDesc(pageable);
        verify(deviceRepository, never()).findByStatus(any(DeviceStatus.class), any());
    }

    // ---------- 6. CommandService.create() — журналювання через AuditService ----------

    @Test
    void create_ShouldRecordAuditEntry_WithCorrectActionName() {
        RemoteCommandRepository commandRepository = org.mockito.Mockito.mock(RemoteCommandRepository.class);
        DeviceRepository deviceRepository = org.mockito.Mockito.mock(DeviceRepository.class);
        ManagementControlProperties properties = new ManagementControlProperties();
        AuditService auditService = org.mockito.Mockito.mock(AuditService.class);

        CommandService commandService = new CommandService(commandRepository, deviceRepository,
                properties, auditService);

        Device device = new Device();
        device.setId(10L);
        device.setStatus(DeviceStatus.ACTIVE);
        when(deviceRepository.findById(10L)).thenReturn(Optional.of(device));
        when(commandRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        commandService.create(10L, RemoteCommandType.TAKE_SCREENSHOT, null, "admin", null);

        ArgumentCaptor<String> actionCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService, times(1))
                .record(eq("admin"), actionCaptor.capture(), eq("DEVICE"), eq("10"), eq(null));
        assertThat(actionCaptor.getValue()).isEqualTo("COMMAND_TAKE_SCREENSHOT");

        // для TAKE_SCREENSHOT статус пристрою не змінюється — deviceRepository.save() не викликається
        verify(deviceRepository, never()).save(any(Device.class));
    }

    // ---------- 9. EnrollmentService.enroll() — деактивація коду реєстрації ----------

    @Test
    void enroll_ShouldDeactivateEnrollmentCode_AfterSuccessfulUse() {
        EnrollmentCodeRepository enrollmentCodeRepository = org.mockito.Mockito.mock(EnrollmentCodeRepository.class);
        DeviceRepository deviceRepository = org.mockito.Mockito.mock(DeviceRepository.class);
        ManagementControlProperties properties = new ManagementControlProperties();
        AuditService auditService = org.mockito.Mockito.mock(AuditService.class);

        EnrollmentService enrollmentService = new EnrollmentService(enrollmentCodeRepository,
                deviceRepository, properties, auditService);

        EnrollmentCode code = new EnrollmentCode();
        code.setCode("VALID-CODE");
        code.setActive(true);
        code.setExpiresAt(Instant.now().plusSeconds(3600));

        when(enrollmentCodeRepository.findByCode("VALID-CODE")).thenReturn(Optional.of(code));
        when(deviceRepository.findByDeviceId("device-mock-1")).thenReturn(Optional.empty());
        when(deviceRepository.save(any(Device.class))).thenAnswer(invocation -> {
            Device d = invocation.getArgument(0);
            d.setId(42L);
            return d;
        });
        when(enrollmentCodeRepository.save(any(EnrollmentCode.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EnrollRequest request = new EnrollRequest("VALID-CODE", "device-mock-1", "pc-mock",
                "Windows 11", "11.0", "0.1.0", "00:11:22:33:44:99", "192.168.1.50", "manager");

        enrollmentService.enroll(request);

        ArgumentCaptor<EnrollmentCode> codeCaptor = ArgumentCaptor.forClass(EnrollmentCode.class);
        verify(enrollmentCodeRepository, times(1)).save(codeCaptor.capture());
        assertThat(codeCaptor.getValue().isActive()).isFalse();
        assertThat(codeCaptor.getValue().getUsedByDeviceId()).isEqualTo("device-mock-1");

        verify(auditService, times(1))
                .record(eq("agent:device-mock-1"), eq("DEVICE_ENROLLED"), eq("DEVICE"), eq("42"), any());
    }
}
