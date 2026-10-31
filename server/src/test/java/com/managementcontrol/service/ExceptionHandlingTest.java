package com.managementcontrol.service;

import com.managementcontrol.common.BadRequestException;
import com.managementcontrol.common.NotFoundException;
import com.managementcontrol.config.ManagementControlProperties;
import com.managementcontrol.domain.Device;
import com.managementcontrol.domain.EnrollmentCode;
import com.managementcontrol.domain.RemoteCommand;
import com.managementcontrol.repository.DeviceRepository;
import com.managementcontrol.repository.EnrollmentCodeRepository;
import com.managementcontrol.repository.RemoteCommandRepository;
import com.managementcontrol.web.agent.dto.EnrollRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Exception Testing: перевіряє реакцію застосунку на ситуації,
 * коли операція не може завершитися успішно — звернення до неіснуючих
 * об'єктів, недопустимі операції та порушення бізнес-правил.
 */
@ExtendWith(MockitoExtension.class)
class ExceptionHandlingTest {

    // ---------- 1. Звернення до об'єкта, якого не існує (DeviceService) ----------

    @Mock
    private DeviceRepository deviceRepositoryForDevice;

    @Mock
    private CommandService commandServiceForDevice;

    @Mock
    private ManagementControlProperties propertiesForDevice;

    @Test
    void get_ShouldThrowNotFoundException_WhenDeviceDoesNotExist() {
        DeviceService deviceService = new DeviceService(deviceRepositoryForDevice,
                commandServiceForDevice, propertiesForDevice);

        when(deviceRepositoryForDevice.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deviceService.get(999L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("999");
    }

    // ---------- 2. Звернення до неіснуючої команди (CommandService) ----------

    @Mock
    private RemoteCommandRepository commandRepository;

    @Mock
    private DeviceRepository deviceRepositoryForCommand;

    @Mock
    private ManagementControlProperties propertiesForCommand;

    @Mock
    private AuditService auditServiceForCommand;

    @Test
    void acknowledge_ShouldThrowNotFoundException_WhenCommandDoesNotExist() {
        CommandService commandService = new CommandService(commandRepository,
                deviceRepositoryForCommand, propertiesForCommand, auditServiceForCommand);

        when(commandRepository.findById(777L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commandService.acknowledge(1L, 777L, "EXECUTED", "OK"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("777");
    }

    // ---------- 3. Недопустима операція: команда належить іншому пристрою ----------

    @Test
    void acknowledge_ShouldThrowBadRequestException_WhenCommandBelongsToAnotherDevice() {
        CommandService commandService = new CommandService(commandRepository,
                deviceRepositoryForCommand, propertiesForCommand, auditServiceForCommand);

        RemoteCommand command = new RemoteCommand();
        command.setId(5L);
        command.setDeviceId(100L);

        when(commandRepository.findById(5L)).thenReturn(Optional.of(command));

        assertThatThrownBy(() -> commandService.acknowledge(200L, 5L, "EXECUTED", "OK"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("іншому пристрою");
    }

    // ---------- 4. Створення команди для неіснуючого пристрою ----------

    @Test
    void create_ShouldThrowNotFoundException_WhenDeviceDoesNotExist() {
        CommandService commandService = new CommandService(commandRepository,
                deviceRepositoryForCommand, propertiesForCommand, auditServiceForCommand);

        when(deviceRepositoryForCommand.findById(555L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commandService.create(555L,
                com.managementcontrol.domain.RemoteCommandType.LOCK, null, "admin", null))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("555");
    }

    // ---------- 5. Порушення бізнес-правила: протермінований код реєстрації ----------

    @Mock
    private EnrollmentCodeRepository enrollmentCodeRepository;

    @Mock
    private DeviceRepository deviceRepositoryForEnrollment;

    @Mock
    private ManagementControlProperties propertiesForEnrollment;

    @Mock
    private AuditService auditServiceForEnrollment;

    @Test
    void enroll_ShouldThrowBadRequestException_WhenEnrollmentCodeIsExpired() {
        EnrollmentService enrollmentService = new EnrollmentService(enrollmentCodeRepository,
                deviceRepositoryForEnrollment, propertiesForEnrollment, auditServiceForEnrollment);

        EnrollmentCode expiredCode = new EnrollmentCode();
        expiredCode.setCode("EXPIRED-CODE");
        expiredCode.setActive(true);
        expiredCode.setExpiresAt(Instant.now().minusSeconds(3600));

        when(enrollmentCodeRepository.findByCode("EXPIRED-CODE")).thenReturn(Optional.of(expiredCode));

        EnrollRequest request = new EnrollRequest("EXPIRED-CODE", "device-x", "pc-x",
                "Windows 11", "11.0", "0.1.0", "00:11:22:33:44:88", "192.168.1.40", "manager");

        assertThatThrownBy(() -> enrollmentService.enroll(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Термін дії");
    }
}
