package com.managementcontrol.web.admin;

import com.managementcontrol.domain.EnrollmentCode;
import com.managementcontrol.repository.EnrollmentCodeRepository;
import com.managementcontrol.service.EnrollmentService;
import com.managementcontrol.web.admin.dto.CreateEnrollmentCodeRequest;
import com.managementcontrol.web.admin.dto.EnrollmentCodeDto;
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
public class EnrollmentController {

    private final EnrollmentService enrollmentService;
    private final EnrollmentCodeRepository repository;

    public EnrollmentController(EnrollmentService enrollmentService, EnrollmentCodeRepository repository) {
        this.enrollmentService = enrollmentService;
        this.repository = repository;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EnrollmentCodeDto> create(@Valid @RequestBody CreateEnrollmentCodeRequest request) {
        EnrollmentCode code = enrollmentService.createCode(request.label(), request.assignedTo(),
                request.boundDeviceId(), request.ttl());
        return ResponseEntity.ok(toDto(code));
    }

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
