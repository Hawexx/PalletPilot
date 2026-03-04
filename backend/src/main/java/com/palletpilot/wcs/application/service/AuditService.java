package com.palletpilot.wcs.application.service;

import com.palletpilot.wcs.domain.model.AuditLog;
import com.palletpilot.wcs.infrastructure.persistence.repository.AuditLogRepository;
import com.palletpilot.wcs.infrastructure.web.CorrelationIdFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Audit service for logging critical operations to the audit_log table.
 *
 * Usage:
 * auditService.log("ORDER_CREATED", "Order", orderId.toString(), userId,
 * "{\"wmsId\":\"abc\"}");
 *
 * Logs are persisted asynchronously to avoid blocking the main thread.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Log an audit event asynchronously.
     *
     * @param action     Action type (e.g., ORDER_CREATED, SYSTEM_STATE_CHANGE)
     * @param entityType Entity type (e.g., Order, Equipment, SystemState)
     * @param entityId   Entity identifier
     * @param userId     User who performed the action (nullable)
     * @param details    JSON string with additional details (nullable)
     */
    @Async
    public void log(String action, String entityType, String entityId, String userId, String details) {
        try {
            AuditLog entry = AuditLog.builder()
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .userId(userId)
                    .details(details)
                    .correlationId(MDC.get(CorrelationIdFilter.MDC_KEY))
                    .build();

            auditLogRepository.save(entry);

            log.debug("[AUDIT] {} | {} {} | user={} | corr={}",
                    action, entityType, entityId, userId, entry.getCorrelationId());
        } catch (Exception e) {
            // Audit must never break business flow
            log.error("[AUDIT] Failed to persist audit log: {} — {}", action, e.getMessage());
        }
    }

    /**
     * Convenience overload without details.
     */
    public void log(String action, String entityType, String entityId, String userId) {
        log(action, entityType, entityId, userId, null);
    }
}
