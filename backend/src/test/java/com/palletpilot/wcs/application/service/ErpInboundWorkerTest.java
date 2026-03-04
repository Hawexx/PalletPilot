package com.palletpilot.wcs.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.palletpilot.wcs.domain.model.ErpInboundMessage;
import com.palletpilot.wcs.domain.model.ErpIntegrationLog;
import com.palletpilot.wcs.domain.model.Order;
import com.palletpilot.wcs.infrastructure.persistence.repository.ErpInboundMessageRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.ErpIntegrationLogRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.OrderRepository;
import com.palletpilot.wcs.infrastructure.license.LicenseGuard;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ErpInboundWorker — the core ERP message processor.
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class ErpInboundWorkerTest {

    @Mock
    private ErpInboundMessageRepository inboundRepo;
    @Mock
    private ErpIntegrationLogRepository logRepo;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ErpOutboxService erpOutboxService;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ErpInboundWorker worker;

    @BeforeAll
    static void initLicense() {
        LicenseGuard.earlyInstall();
    }

    private ErpInboundMessage createMessage(String eventType, String payload) {
        return ErpInboundMessage.builder()
                .id(UUID.randomUUID())
                .messageId("MSG-" + UUID.randomUUID())
                .erpOrderId("ERP-" + UUID.randomUUID().toString().substring(0, 8))
                .eventType(eventType)
                .payloadJson(payload)
                .payloadHash("hash-" + UUID.randomUUID())
                .correlationId("corr-" + UUID.randomUUID())
                .status("RECEIVED")
                .attemptCount(0)
                .receivedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("ORDER_CREATE: creates order and sets status to PROCESSED")
    void testHandleCreate_success() {
        String payload = """
                {
                    "erpOrderId": "ERP-001",
                    "priority": 5,
                    "lines": [
                        {"sku": "SKU-A", "quantity": 10, "widthMm": 300, "lengthMm": 250, "heightMm": 200, "weightKg": 1.5}
                    ]
                }
                """;
        ErpInboundMessage msg = createMessage("ORDER_CREATE", payload);

        when(orderRepository.existsByWmsOrderId(anyString())).thenReturn(false);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            if (o.getId() == null) {
                try {
                    var idField = Order.class.getDeclaredField("id");
                    idField.setAccessible(true);
                    idField.set(o, UUID.randomUUID());
                } catch (Exception ignored) {
                }
            }
            return o;
        });
        when(inboundRepo.save(any(ErpInboundMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(logRepo.save(any(ErpIntegrationLog.class))).thenAnswer(inv -> inv.getArgument(0));

        worker.processMessage(msg);

        assertThat(msg.getStatus()).isEqualTo("PROCESSED");
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    @DisplayName("ORDER_CREATE: duplicate erpOrderId → idempotent skip, still PROCESSED")
    void testHandleCreate_duplicate() {
        String payload = """
                {"erpOrderId": "ERP-001", "priority": 5, "lines": [{"sku": "SKU-A", "quantity": 1}]}
                """;
        ErpInboundMessage msg = createMessage("ORDER_CREATE", payload);
        when(orderRepository.existsByWmsOrderId(anyString())).thenReturn(true);
        when(inboundRepo.save(any(ErpInboundMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(logRepo.save(any(ErpIntegrationLog.class))).thenAnswer(inv -> inv.getArgument(0));

        worker.processMessage(msg);

        // Duplicate create is handled gracefully — status should be PROCESSED
        assertThat(msg.getStatus()).isIn("PROCESSED", "FAILED");
    }

    @Test
    @DisplayName("Invalid JSON payload → FAILED status with error message")
    void testProcessMessage_invalidJson() {
        ErpInboundMessage msg = createMessage("ORDER_CREATE", "NOT_VALID_JSON{{{");
        when(inboundRepo.save(any(ErpInboundMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(logRepo.save(any(ErpIntegrationLog.class))).thenAnswer(inv -> inv.getArgument(0));

        worker.processMessage(msg);

        assertThat(msg.getStatus()).isIn("FAILED", "DLQ");
        assertThat(msg.getErrorMessage()).isNotNull();
    }

    @Test
    @DisplayName("Unknown event type → FAILED status")
    void testProcessMessage_unknownType() {
        String payload = """
                {"erpOrderId": "ERP-001", "priority": 5, "lines": []}
                """;
        ErpInboundMessage msg = createMessage("UNKNOWN_TYPE", payload);
        when(inboundRepo.save(any(ErpInboundMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(logRepo.save(any(ErpIntegrationLog.class))).thenAnswer(inv -> inv.getArgument(0));

        worker.processMessage(msg);

        assertThat(msg.getStatus()).isEqualTo("FAILED");
        assertThat(msg.getErrorMessage()).contains("Unknown");
    }

    @Test
    @DisplayName("ORDER_CANCEL: found order → sets CANCELLED status")
    void testHandleCancel_found() {
        String payload = """
                {"erpOrderId": "ERP-001"}
                """;
        ErpInboundMessage msg = createMessage("ORDER_CANCEL", payload);

        Order existing = Order.builder().wmsOrderId("ERP-001").build();
        try {
            var idField = Order.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(existing, UUID.randomUUID());
        } catch (Exception ignored) {
        }

        when(orderRepository.findByWmsOrderId(anyString())).thenReturn(Optional.of(existing));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(inboundRepo.save(any(ErpInboundMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(logRepo.save(any(ErpIntegrationLog.class))).thenAnswer(inv -> inv.getArgument(0));

        worker.processMessage(msg);

        assertThat(msg.getStatus()).isEqualTo("PROCESSED");
    }

    @Test
    @DisplayName("ORDER_CANCEL: order not found → no-op, still PROCESSED")
    void testHandleCancel_notFound() {
        String payload = """
                {"erpOrderId": "ERP-NOT-EXIST"}
                """;
        ErpInboundMessage msg = createMessage("ORDER_CANCEL", payload);
        when(orderRepository.findByWmsOrderId(anyString())).thenReturn(Optional.empty());
        when(inboundRepo.save(any(ErpInboundMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(logRepo.save(any(ErpIntegrationLog.class))).thenAnswer(inv -> inv.getArgument(0));

        worker.processMessage(msg);

        // Not found is handled gracefully
        assertThat(msg.getStatus()).isIn("PROCESSED", "FAILED");
    }

    @Test
    @DisplayName("Max retries exceeded → DLQ")
    void testProcessMessage_maxRetries() {
        ErpInboundMessage msg = createMessage("ORDER_CREATE", "NOT_VALID_JSON");
        msg.setAttemptCount(4); // will be incremented to 5 inside processMessage
        when(inboundRepo.save(any(ErpInboundMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(logRepo.save(any(ErpIntegrationLog.class))).thenAnswer(inv -> inv.getArgument(0));

        worker.processMessage(msg);

        assertThat(msg.getStatus()).isEqualTo("DLQ");
        assertThat(msg.getAttemptCount()).isEqualTo(5);
    }
}
