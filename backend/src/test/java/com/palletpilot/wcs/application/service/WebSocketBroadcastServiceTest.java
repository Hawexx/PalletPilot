package com.palletpilot.wcs.application.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WebSocketBroadcastService.
 * Verifies correct topic routing, envelope structure, and failure safety.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NullableProblems")
class WebSocketBroadcastServiceTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private WebSocketBroadcastService service;

    @Test
    @DisplayName("sendAlert → broadcasts to /topic/alerts with ALERT type")
    void sendAlert_broadcastsCorrectly() {
        var alertData = Map.of("severity", "CRITICAL", "message", "Conveyor jam");

        service.sendAlert(alertData);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/alerts"), captor.capture());

        Map<String, Object> envelope = captor.getValue();
        assertThat(envelope.get("type")).isEqualTo("ALERT");
        assertThat(envelope.get("timestamp")).isNotNull();
        assertThat(envelope.get("data")).isEqualTo(alertData);
    }

    @Test
    @DisplayName("sendDashboardUpdate → broadcasts to /topic/dashboard")
    void sendDashboardUpdate_broadcastsCorrectly() {
        var stats = Map.of("activeOrders", 5);

        service.sendDashboardUpdate(stats);

        verify(messagingTemplate).convertAndSend(eq("/topic/dashboard"),
                argThat((Map<String, Object> m) -> "DASHBOARD_UPDATE".equals(m.get("type"))
                        && m.get("data").equals(stats)));
    }

    @Test
    @DisplayName("sendErpEvent → broadcasts to /topic/erp with custom event type")
    void sendErpEvent_broadcastsCorrectly() {
        var payload = Map.of("orderId", "ORD-123");

        service.sendErpEvent("SHIPMENT_ENQUEUED", payload);

        verify(messagingTemplate).convertAndSend(eq("/topic/erp"),
                argThat((Map<String, Object> m) -> "SHIPMENT_ENQUEUED".equals(m.get("type"))));
    }

    @Test
    @DisplayName("sendMovuEvent → broadcasts to /topic/movu")
    void sendMovuEvent_broadcastsCorrectly() {
        var payload = Map.of("missionId", "M-001", "newStatus", "COMPLETED");

        service.sendMovuEvent("MISSION_UPDATE", payload);

        verify(messagingTemplate).convertAndSend(eq("/topic/movu"),
                argThat((Map<String, Object> m) -> "MISSION_UPDATE".equals(m.get("type"))));
    }

    @Test
    @DisplayName("Broadcast failure must NOT propagate — business logic stays safe")
    void broadcastFailure_doesNotPropagate() {
        doThrow(new RuntimeException("WS connection lost"))
                .when(messagingTemplate).convertAndSend(anyString(), any(Map.class));

        // Must NOT throw
        assertThatCode(() -> service.sendAlert(Map.of("test", "data")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("sendOrderUpdate → broadcasts to /topic/orders")
    void sendOrderUpdate_broadcastsCorrectly() {
        var order = Map.of("id", "uuid-123", "status", "COMPLETED");

        service.sendOrderUpdate(order);

        verify(messagingTemplate).convertAndSend(eq("/topic/orders"),
                argThat((Map<String, Object> m) -> "ORDER_UPDATE".equals(m.get("type"))));
    }
}
