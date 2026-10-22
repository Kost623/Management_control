package com.managementcontrol.web.admin;

import com.managementcontrol.domain.AuditLog;
import com.managementcontrol.repository.AuditLogRepository;
import com.managementcontrol.web.admin.dto.AuditLogDto;
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
public class AuditController {

    private final AuditLogRepository repository;

    public AuditController(AuditLogRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public Page<AuditLogDto> list(@RequestParam(defaultValue = "0") int page,
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
