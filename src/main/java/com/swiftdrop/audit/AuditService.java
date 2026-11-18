package com.swiftdrop.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuditService {

    private final AuditLogRepository logs;

    public AuditService(AuditLogRepository logs) {
        this.logs = logs;
    }

    /** Saved in the caller's transaction, so the audit row and the change commit together. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID actorId, String action, String targetType, Object targetId,
                       String oldValue, String newValue) {
        AuditLog log = new AuditLog();
        log.setActorId(actorId);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(String.valueOf(targetId));
        log.setOldValue(oldValue);
        log.setNewValue(newValue);
        logs.save(log);
    }
}
