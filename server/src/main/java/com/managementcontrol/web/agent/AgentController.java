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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 * Усі запити (окрім /enroll) автентифікуються device-токеном (mc_dev_...)
 * у заголовку {@code Authorization: Bearer mc_dev_...}.
 */
@RestController
@RequestMapping("/api/v1/agent")
@Tag(name = "Agent API", description = "Ендпоінти, якими користується агент на клієнтському пристрої")
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

    @Operation(
            summary = "Реєстрація пристрою",
            description = "Обмінює одноразовий код реєстрації (enrollment code) на постійний " +
                    "device-токен (`mc_dev_...`), яким агент автентифікує всі подальші запити. " +
                    "Автентифікація не потрібна — ендпоінт відкритий."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Пристрій зареєстровано, токен видано",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = EnrollResponse.class))),
            @ApiResponse(responseCode = "400", description = "Невалідне тіло запиту (відсутні обов'язкові поля)"),
            @ApiResponse(responseCode = "404", description = "Код реєстрації не знайдено, вже використаний або протермінований")
    })
    @PostMapping("/enroll")
    public ResponseEntity<EnrollResponse> enroll(@Valid @RequestBody EnrollRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(enrollmentService.enroll(request));
    }

    @Operation(
            summary = "Heartbeat",
            description = "Періодичний сигнал живості агента. Оновлює `last_seen_at`, " +
                    "версію агента та IP-адресу пристрою в БД."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Прийнято"),
            @ApiResponse(responseCode = "401", description = "Відсутній або недійсний device-токен")
    })
    @SecurityRequirement(name = "deviceToken")
    @PostMapping("/heartbeat")
    public ResponseEntity<IngestResponse> heartbeat(@Valid @RequestBody HeartbeatRequest request) {
        Device device = currentDevice.require();
        telemetryService.touch(device, request.agentVersion(), request.ipAddress());
        return ResponseEntity.ok(IngestResponse.of(1));
    }

    @Operation(
            summary = "Надіслати пакет сесій використання програм",
            description = "Приймає масив інтервалів активності програм (назва процесу, " +
                    "час початку/кінця, чи був простій, чи вікно було на передньому плані) " +
                    "і зберігає їх, прив'язаними до поточного пристрою."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Пакет прийнято; у відповіді — кількість прийнятих записів"),
            @ApiResponse(responseCode = "400", description = "Невалідні дані в одному чи кількох елементах пакета"),
            @ApiResponse(responseCode = "401", description = "Відсутній або недійсний device-токен")
    })
    @SecurityRequirement(name = "deviceToken")
    @PostMapping("/usage-sessions")
    public ResponseEntity<IngestResponse> usageSessions(@Valid @RequestBody UsageSessionBatchRequest request) {
        Device device = currentDevice.require();
        int accepted = telemetryService.ingestUsageSessions(device, request.sessions());
        telemetryService.touch(device, null, null);
        return ResponseEntity.ok(IngestResponse.of(accepted));
    }

    @Operation(
            summary = "Надіслати пакет активності браузера",
            description = "Приймає масив відвіданих URL/доменів з тривалістю активного перебування на сторінці."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Пакет прийнято"),
            @ApiResponse(responseCode = "400", description = "Невалідні дані в пакеті"),
            @ApiResponse(responseCode = "401", description = "Відсутній або недійсний device-токен")
    })
    @SecurityRequirement(name = "deviceToken")
    @PostMapping("/browsing")
    public ResponseEntity<IngestResponse> browsing(@Valid @RequestBody BrowsingBatchRequest request) {
        Device device = currentDevice.require();
        int accepted = telemetryService.ingestBrowsing(device, request.activities());
        telemetryService.touch(device, null, null);
        return ResponseEntity.ok(IngestResponse.of(accepted));
    }

    @Operation(
            summary = "Надіслати пакет подій процесів",
            description = "Приймає масив подій запуску (`STARTED`) та завершення (`EXITED`) процесів на пристрої."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Пакет прийнято"),
            @ApiResponse(responseCode = "400", description = "Невалідні дані в пакеті"),
            @ApiResponse(responseCode = "401", description = "Відсутній або недійсний device-токен")
    })
    @SecurityRequirement(name = "deviceToken")
    @PostMapping("/processes")
    public ResponseEntity<IngestResponse> processes(@Valid @RequestBody ProcessEventBatchRequest request) {
        Device device = currentDevice.require();
        int accepted = telemetryService.ingestProcessEvents(device, request.events());
        telemetryService.touch(device, null, null);
        return ResponseEntity.ok(IngestResponse.of(accepted));
    }

    @Operation(
            summary = "Надіслати геолокацію пристрою",
            description = "Приймає одну точку координат (широта, довгота, за наявності — точність, " +
                    "висота, швидкість) з часом фіксації."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Координати прийнято"),
            @ApiResponse(responseCode = "400", description = "Невалідні координати"),
            @ApiResponse(responseCode = "401", description = "Відсутній або недійсний device-токен")
    })
    @SecurityRequirement(name = "deviceToken")
    @PostMapping("/location")
    public ResponseEntity<IngestResponse> location(@Valid @RequestBody LocationRequest request) {
        Device device = currentDevice.require();
        telemetryService.ingestLocation(device, request);
        telemetryService.touch(device, null, null);
        return ResponseEntity.ok(IngestResponse.of(1));
    }

    @Operation(
            summary = "Очікування команд від сервера (long-poll)",
            description = "Блокує з'єднання до появи нової команди для пристрою або до таймауту. " +
                    "Повертає порожній список, якщо команд немає й таймаут вичерпано."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Список команд (може бути порожнім)"),
            @ApiResponse(responseCode = "401", description = "Відсутній або недійсний device-токен")
    })
    @SecurityRequirement(name = "deviceToken")
    @GetMapping("/commands")
    public List<AgentCommandDto> commands(
            @Parameter(description = "Максимальний час очікування в секундах (1–120). " +
                    "Якщо не вказано — використовується значення з конфігурації сервера.")
            @RequestParam(required = false) Long timeoutSeconds) {
        Device device = currentDevice.require();
        Duration timeout = timeoutSeconds == null
                ? properties.getCommandPollTimeout()
                : Duration.ofSeconds(Math.min(Math.max(timeoutSeconds, 1), 120));
        return commandService.poll(device.getId(), timeout);
    }

    @Operation(
            summary = "Підтвердити виконання команди",
            description = "Агент повідомляє результат виконання команди: статус " +
                    "(`DELIVERED`/`EXECUTED`/`FAILED`) і, за потреби, текстовий результат."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Підтвердження прийнято"),
            @ApiResponse(responseCode = "400", description = "commandId у тілі запиту не збігається з commandId у URL"),
            @ApiResponse(responseCode = "401", description = "Відсутній або недійсний device-токен"),
            @ApiResponse(responseCode = "404", description = "Команду з таким ID не знайдено для цього пристрою")
    })
    @SecurityRequirement(name = "deviceToken")
    @PostMapping("/commands/{commandId}/ack")
    public ResponseEntity<Void> ack(
            @Parameter(description = "Ідентифікатор команди (той самий, що в тілі запиту)")
            @PathVariable Long commandId,
            @Valid @RequestBody CommandAckRequest request) {
        Device device = currentDevice.require();
        if (!request.commandId().equals(commandId)) {
            throw new com.managementcontrol.common.BadRequestException("commandId у запиті та URL не збігаються");
        }
        commandService.acknowledge(device.getId(), commandId, request.status(), request.result());
        return ResponseEntity.noContent().build();
    }
}