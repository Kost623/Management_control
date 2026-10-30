package com.managementcontrol.web.admin;

import com.managementcontrol.domain.DeviceStatus;
import com.managementcontrol.service.DeviceService;
import com.managementcontrol.web.admin.dto.DeviceDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/** Керування пристроями: перегляд, блокування, розблокування. */
@RestController
@RequestMapping("/api/v1/admin/devices")
@Tag(name = "Admin: Devices", description = "Перегляд пристроїв та керування їх станом")
@SecurityRequirement(name = "bearerAuth")
public class DeviceAdminController {

    private final DeviceService deviceService;

    public DeviceAdminController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @Operation(
            summary = "Список пристроїв",
            description = "Повертає сторінку пристроїв з опціональним фільтром за статусом " +
                    "(`ACTIVE`, `LOCKED`, `OFFLINE`, `BLOCKED`), відсортовану за `id` у спадному порядку."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Сторінка пристроїв"),
            @ApiResponse(responseCode = "401", description = "Відсутній або недійсний JWT")
    })
    @GetMapping
    public Page<DeviceDto> list(
            @Parameter(description = "Фільтр за статусом пристрою; якщо не вказано — усі статуси")
            @RequestParam(required = false) DeviceStatus status,
            @Parameter(description = "Номер сторінки, починаючи з 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Розмір сторінки (1–200)")
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200),
                Sort.by(Sort.Direction.DESC, "id"));
        return deviceService.list(status, pageable).map(DeviceDto::from);
    }

    @Operation(summary = "Отримати пристрій за ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Дані пристрою"),
            @ApiResponse(responseCode = "401", description = "Відсутній або недійсний JWT"),
            @ApiResponse(responseCode = "404", description = "Пристрій з таким ID не знайдено")
    })
    @GetMapping("/{id}")
    public DeviceDto get(
            @Parameter(description = "Внутрішній ID пристрою (не device_id)")
            @PathVariable Long id) {
        return DeviceDto.from(deviceService.get(id));
    }

    @Operation(
            summary = "Заблокувати пристрій",
            description = "Тимчасове блокування: блокує екран і робочу сесію. Знімається через /unlock."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Пристрій заблоковано"),
            @ApiResponse(responseCode = "401", description = "Відсутній або недійсний JWT"),
            @ApiResponse(responseCode = "403", description = "Авторизований користувач не має ролі ADMIN"),
            @ApiResponse(responseCode = "404", description = "Пристрій з таким ID не знайдено")
    })
    @PostMapping("/{id}/lock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DeviceDto> lock(
            @PathVariable Long id,
            @Parameter(description = "Причина блокування (відображається в lock_reason)")
            @RequestParam(required = false) String reason,
            Principal principal) {
        return ResponseEntity.ok(DeviceDto.from(deviceService.lock(id, reason, principal.getName())));
    }

    @Operation(summary = "Зняти блокування пристрою")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Блокування знято"),
            @ApiResponse(responseCode = "401", description = "Відсутній або недійсний JWT"),
            @ApiResponse(responseCode = "403", description = "Авторизований користувач не має ролі ADMIN"),
            @ApiResponse(responseCode = "404", description = "Пристрій з таким ID не знайдено")
    })
    @PostMapping("/{id}/unlock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DeviceDto> unlock(@PathVariable Long id, Principal principal) {
        return ResponseEntity.ok(DeviceDto.from(deviceService.unlock(id, principal.getName())));
    }

    @Operation(
            summary = "Заблокувати пристрій назавжди",
            description = "На відміну від /lock — незворотна дія. Агент більше не зможе " +
                    "автентифікуватися цим device-токеном."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Пристрій заблоковано назавжди"),
            @ApiResponse(responseCode = "401", description = "Відсутній або недійсний JWT"),
            @ApiResponse(responseCode = "403", description = "Авторизований користувач не має ролі ADMIN"),
            @ApiResponse(responseCode = "404", description = "Пристрій з таким ID не знайдено")
    })
    @PostMapping("/{id}/block")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DeviceDto> block(
            @PathVariable Long id,
            @Parameter(description = "Причина блокування")
            @RequestParam(required = false) String reason,
            Principal principal) {
        return ResponseEntity.ok(DeviceDto.from(deviceService.block(id, reason, principal.getName())));
    }
}