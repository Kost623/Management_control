package com.managementcontrol.web.admin;

import com.managementcontrol.domain.RemoteCommand;
import com.managementcontrol.repository.RemoteCommandRepository;
import com.managementcontrol.service.CommandService;
import com.managementcontrol.web.admin.dto.CommandDto;
import com.managementcontrol.web.admin.dto.CreateCommandRequest;
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
public class CommandAdminController {

    private final CommandService commandService;
    private final RemoteCommandRepository repository;

    public CommandAdminController(CommandService commandService, RemoteCommandRepository repository) {
        this.commandService = commandService;
        this.repository = repository;
    }

    @PostMapping("/devices/{deviceId}/commands")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CommandDto> create(@PathVariable Long deviceId,
                                             @Valid @RequestBody CreateCommandRequest request,
                                             Principal principal) {
        RemoteCommand command = commandService.create(deviceId, request.type(), request.payload(),
                principal.getName(), request.ttl());
        return ResponseEntity.ok(toDto(command));
    }

    @GetMapping("/devices/{deviceId}/commands")
    public Page<CommandDto> listForDevice(@PathVariable Long deviceId,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200),
                Sort.by(Sort.Direction.DESC, "id"));
        return repository.findByDeviceIdOrderByCreatedAtDesc(deviceId, pageable).map(CommandAdminController::toDto);
    }

    @GetMapping("/commands")
    public Page<CommandDto> listAll(@RequestParam(defaultValue = "0") int page,
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
