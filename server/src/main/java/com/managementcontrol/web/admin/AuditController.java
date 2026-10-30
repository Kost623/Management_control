package com.managementcontrol.web.admin;

import com.managementcontrol.domain.AuditLog;
import com.managementcontrol.repository.AuditLogRepository;
import com.managementcontrol.web.admin.dto.AuditLogDto;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Журнал дій адміністраторів. */
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@Tag(name = "Admin: Audit", description = "Журнал дій адміністраторів (хто що зробив і коли)")
@SecurityRequirement(name = "bearerAuth")
public class AuditController {

    private final AuditLogRepository repository;

    public AuditController(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Operation(
            summary = "Журнал дій адміністраторів",
            description = "Повертає записи аудиту (хто, яку дію, над яким об'єктом, коли), " +
                    "відсортовані від найновіших."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Сторінка записів журналу"),
            @ApiResponse(responseCode = "401", description = "Відсутній або недійсний JWT")
    })
    @GetMapping
    public Page<AuditLogDto> list(
            @Parameter(description = "Номер сторінки, починаючи з 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Розмір сторінки (1–200)")
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return repository.findAllByOrderByCreatedAtDesc(pageable).map(AuditController::toDto);
    }

    private static AuditLogDto toDto(AuditLog log) {
        return new AuditLogDto(log.getId(), log.getActor(), log.getAction(), log.getTargetType(),
                log.getTargetId(), log.getDetails(), log.getCreatedAt());
    }
}