package com.managementcontrol.web.admin;

import com.managementcontrol.domain.EnrollmentCode;
import com.managementcontrol.repository.EnrollmentCodeRepository;
import com.managementcontrol.service.EnrollmentService;
import com.managementcontrol.web.admin.dto.CreateEnrollmentCodeRequest;
import com.managementcontrol.web.admin.dto.EnrollmentCodeDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Коди реєстрації пристроїв. */
@RestController
@RequestMapping("/api/v1/admin/enrollment-codes")
@Tag(name = "Admin: Enrollment", description = "Керування кодами реєстрації нових пристроїв")
@SecurityRequirement(name = "bearerAuth")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;
    private final EnrollmentCodeRepository repository;

    public EnrollmentController(EnrollmentService enrollmentService, EnrollmentCodeRepository repository) {
        this.enrollmentService = enrollmentService;
        this.repository = repository;
    }

    @Operation(
            summary = "Створити код реєстрації",
            description = "Генерує одноразовий код, яким новий агент зможе обміняти себе на device-токен " +
                    "через POST /api/v1/agent/enroll. За потреби код можна прив'язати до конкретного " +
                    "`device_id` (`boundDeviceId`), щоб ним міг скористатись лише цей пристрій."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Код створено"),
            @ApiResponse(responseCode = "400", description = "Невалідне тіло запиту"),
            @ApiResponse(responseCode = "401", description = "Відсутній або недійсний JWT"),
            @ApiResponse(responseCode = "403", description = "Авторизований користувач не має ролі ADMIN")
    })
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EnrollmentCodeDto> create(@Valid @RequestBody CreateEnrollmentCodeRequest request) {
        EnrollmentCode code = enrollmentService.createCode(request.label(), request.assignedTo(),
                request.boundDeviceId(), request.ttl());
        return ResponseEntity.ok(toDto(code));
    }

    @Operation(
            summary = "Список активних кодів реєстрації",
            description = "Повертає всі ще не деактивовані коди (включно з протермінованими, якщо " +
                    "вони формально не позначені неактивними), відсортовані від найновіших."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Список кодів"),
            @ApiResponse(responseCode = "401", description = "Відсутній або недійсний JWT")
    })
    @GetMapping
    public List<EnrollmentCodeDto> list() {
        return repository.findByActiveTrueOrderByCreatedAtDesc().stream().map(EnrollmentController::toDto).toList();
    }

    private static EnrollmentCodeDto toDto(EnrollmentCode code) {
        return new EnrollmentCodeDto(code.getId(), code.getCode(), code.getLabel(), code.getAssignedTo(),
                code.getBoundDeviceId(), code.getCreatedAt(), code.getExpiresAt(), code.getUsedAt(),
                code.getUsedByDeviceId(), code.isActive());
    }
}