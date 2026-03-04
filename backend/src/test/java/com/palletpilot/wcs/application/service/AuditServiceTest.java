package com.palletpilot.wcs.application.service;

import com.palletpilot.wcs.domain.model.AuditLog;
import com.palletpilot.wcs.infrastructure.persistence.repository.AuditLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuditService — async audit logging.
 * Tests persistence, MDC correlation, error resilience, and convenience
 * overload.
 */
@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditService service;

    @Test
    @DisplayName("log: persists audit entry with all fields")
    void log_persistsEntry() {
        MDC.put("correlationId", "CID-123");
        try {
            service.log("ORDER_CREATED", "Order", "ORD-1", "admin", "{\"wmsId\":\"abc\"}");

            verify(auditLogRepository).save(argThat(entry -> {
                AuditLog a = (AuditLog) entry;
                return "ORDER_CREATED".equals(a.getAction())
                        && "Order".equals(a.getEntityType())
                        && "ORD-1".equals(a.getEntityId())
                        && "admin".equals(a.getUserId())
                        && "{\"wmsId\":\"abc\"}".equals(a.getDetails())
                        && "CID-123".equals(a.getCorrelationId());
            }));
        } finally {
            MDC.remove("correlationId");
        }
    }

    @Test
    @DisplayName("log: convenience overload without details")
    void log_convenienceOverload() {
        service.log("STATE_CHANGE", "Equipment", "EQ-1", "system");

        verify(auditLogRepository).save(argThat(entry -> {
            AuditLog a = (AuditLog) entry;
            return a.getDetails() == null && "STATE_CHANGE".equals(a.getAction());
        }));
    }

    @Test
    @DisplayName("log: repository throws → does NOT propagate (audit must not break business)")
    void log_repositoryFails_doesNotPropagate() {
        doThrow(new RuntimeException("DB down")).when(auditLogRepository).save(any());

        // Must not throw
        service.log("FAIL_TEST", "Test", "T-1", "user", null);
    }
}
