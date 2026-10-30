package com.managementcontrol.web.admin;

import com.managementcontrol.domain.RemoteCommand;
import com.managementcontrol.repository.RemoteCommandRepository;
import com.managementcontrol.service.CommandService;
import com.managementcontrol.web.admin.dto.CommandDto;
import com.managementcontrol.web.admin.dto.CreateCommandRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/** Керування командами, які надсилаються на пристрій. */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin: Commands", description = "Створення та перегляд дистанційних команд для пристроїв")
@SecurityRequirement(name = "bearerAuth")
public class CommandAdminController {

    private final CommandService commandService;
    private final RemoteCommandRepository repository;

    public CommandAdminController(CommandService commandService, RemoteCommandRepository repository) {
        this.commandService = commandService;
        this.repository = repository;
    }

    @Operation(
            summary = "Створити команду для пристрою",
            description = "Ставить команду в чергу (статус `PENDING`); агент отримає її через " +
                    "long-poll GET /api/v1/agent/commands. Типи: `LOCK`, `UNLOCK`, `TAKE_SCREENSHOT`, " +
                    "`SHOW_MESSAGE`, `KILL_PROCESS`, `RUN_COMMAND`, `REBOOT`."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Команду створено"),
            @ApiResponse(responseCode = "400", description = "Невалідне тіло запиту"),
            @ApiResponse(responseCode = "401", description = "Відсутній або недійсний JWT"),
            @ApiResponse(responseCode = "403", description = "Авторизований користувач не має ролі ADMIN"),
            @ApiResponse(responseCode = "404", description = "Пристрій з таким ID не знайдено")
    })
    @PostMapping("/devices/{deviceId}/commands")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CommandDto> create(
            @Parameter(description = "Внутрішній ID пристрою")
            @PathVariable Long deviceId,
            @Valid @RequestBody CreateCommandRequest request,
            Principal principal) {
        RemoteCommand command = commandService.create(deviceId, request.type(), request.payload(),
                principal.getName(), request.ttl());
        return ResponseEntity.ok(toDto(command));
    }

    @Operation(summary = "Історія команд конкретного пристрою")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Сторінка команд"),
            @ApiResponse(responseCode = "401", description = "Відсутній або недійсний JWT")
    })
    @GetMapping("/devices/{deviceId}/commands")
    public Page<CommandDto> listForDevice(
            @Parameter(description = "Внутрішній ID пристрою")
            @PathVariable Long deviceId,
            @Parameter(description = "Номер сторінки, починаючи з 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Розмір сторінки (1–200)")
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200),
                Sort.by(Sort.Direction.DESC, "id"));
        return repository.findByDeviceIdOrderByCreatedAtDesc(deviceId, pageable).map(CommandAdminController::toDto);
    }

    @Operation(summary = "Усі команди в системі", description = "Історія команд по всіх пристроях одразу.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Сторінка команд"),
            @ApiResponse(responseCode = "401", description = "Відсутній або недійсний JWT")
    })
    @GetMapping("/commands")
    public Page<CommandDto> listAll(
            @Parameter(description = "Номер сторінки, починаючи з 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Розмір сторінки (1–200)")
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200),
                Sort.by(Sort.Direction.DESC, "id"));
        return repository.findAllByOrderByCreatedAtDesc(pageable).map(CommandAdminController::toDto);
    }

    private static CommandDto toDto(RemoteCommand command) {
        return new CommandDto(command.getId(), command.getDeviceId(), command.getType().name(),
                command.getStatus().name(), command.getPayload(), command.getCreatedAt(),
                command.getDeliveredAt(), command.getExecutedAt(), command.getExpiresAt(),
                command.getCreatedBy(), command.getResult());
    }
}