package com.managementcontrol.service;

import com.managementcontrol.domain.AuditLog;
import com.managementcontrol.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Журналювання дій адміністраторів. */
@Service
public class AuditService {

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String actor, String action, String targetType, String targetId, String details) {
        AuditLog entry = new AuditLog();
        entry.setActor(actor == null ? "system" : actor);
        entry.setAction(action);
        entry.setTargetType(targetType);
        entry.setTargetId(targetId);
        entry.setDetails(details);
        entry.setCreatedAt(java.time.Instant.now());
        repository.save(entry);
    }
}
