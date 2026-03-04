package com.palletpilot.wcs.application.service;

import com.palletpilot.wcs.domain.model.ErpOutboxMessage;
import com.palletpilot.wcs.domain.model.Order;
import com.palletpilot.wcs.infrastructure.persistence.repository.ErpOutboxMessageRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ErpOutboxService — outbox pattern for ERP confirmations.
 */
@ExtendWith(MockitoExtension.class)
class ErpOutboxServiceTest {

    @Mock
    private ErpOutboxMessageRepository outboxRepo;
    @Mock
    private OrderRepository orderRepository;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();
    @Mock
    private WebSocketBroadcastService wsBroadcast;

    @InjectMocks
    private ErpOutboxService service;

    private Order createTestOrder() {
        Order order = Order.builder().wmsOrderId("ERP-001").build();
        try {
            var idField = Order.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(order, UUID.randomUUID());
        } catch (Exception ignored) {
        }
        return order;
    }

    @Test
    @DisplayName("Auto-confirm disabled → no-op")
    void testEnqueue_autoConfirmDisabled() {
        ReflectionTestUtils.setField(service, "autoConfirm", false);
        ReflectionTestUtils.setField(service, "callbackUrl", "http://erp.example.com/callback");

        service.enqueueShipmentConfirmation(createTestOrder());

        verify(outboxRepo, never()).save(any());
    }

    @Test
    @DisplayName("No callback URL → no-op")
    void testEnqueue_noCallbackUrl() {
        ReflectionTestUtils.setField(service, "autoConfirm", true);
        ReflectionTestUtils.setField(service, "callbackUrl", "");

        service.enqueueShipmentConfirmation(createTestOrder());

        verify(outboxRepo, never()).save(any());
    }

    @Test
    @DisplayName("Auto-confirm enabled + callback URL → enqueues message")
    void testEnqueue_success() {
        ReflectionTestUtils.setField(service, "autoConfirm", true);
        ReflectionTestUtils.setField(service, "callbackUrl", "http://erp.example.com/callback");
        when(outboxRepo.existsByIdempotencyKey(anyString())).thenReturn(false);
        when(outboxRepo.save(any(ErpOutboxMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        Order order = createTestOrder();
        service.enqueueShipmentConfirmation(order);

        ArgumentCaptor<ErpOutboxMessage> captor = ArgumentCaptor.forClass(ErpOutboxMessage.class);
        verify(outboxRepo).save(captor.capture());

        ErpOutboxMessage saved = captor.getValue();
        assertThat(saved.getMessageType()).isEqualTo("SHIPMENT_CONFIRM");
        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getTargetUrl()).isEqualTo("http://erp.example.com/callback");
        assertThat(saved.getIdempotencyKey()).contains("SHIPMENT_CONFIRM:");
        assertThat(saved.getRequestJson()).contains("ERP-001");
    }

    @Test
    @DisplayName("Duplicate idempotency key → no-op (idempotent)")
    void testEnqueue_idempotent() {
        ReflectionTestUtils.setField(service, "autoConfirm", true);
        ReflectionTestUtils.setField(service, "callbackUrl", "http://erp.example.com/callback");
        when(outboxRepo.existsByIdempotencyKey(anyString())).thenReturn(true);

        service.enqueueShipmentConfirmation(createTestOrder());

        verify(outboxRepo, never()).save(any());
    }
}
