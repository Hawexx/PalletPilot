package com.palletpilot.wcs.infrastructure.movu;

import com.palletpilot.wcs.application.service.MovuEventIngestService;
import com.palletpilot.wcs.infrastructure.movu.config.MovuSecurityProperties;
import com.palletpilot.wcs.infrastructure.movu.dto.MovuMissionPayload;
import com.palletpilot.wcs.infrastructure.movu.dto.MovuNotificationEnvelope;
import com.palletpilot.wcs.infrastructure.movu.security.MovuWebhookAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MovuCallbackController.
 * Tests webhook processing, timestamp staleness, batch handling,
 * and error resilience (callback must always return 200 to prevent aSTC
 * buffering).
 */
@ExtendWith(MockitoExtension.class)
class MovuCallbackControllerTest {

    @Mock
    private MovuWebhookParser webhookParser;

    @Mock
    private MovuEventIngestService eventIngestService;

    @Mock
    private MovuSecurityProperties securityProps;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private MovuCallbackController controller;

    // ── POST /callback (single) ──

    @Test
    @DisplayName("handleCallback → new event returns 200 OK")
    void handleCallback_newEvent_returns200() {
        Map<String, Object> payload = Map.of("notificationType", "MissionDropped");
        UUID equipmentId = UUID.randomUUID();

        MovuNotificationEnvelope envelope = MovuNotificationEnvelope.builder()
                .notificationType("MissionDropped")
                .notificationId("notif-001")
                .timeStamp(Instant.now().toString())
                .missionPayload(MovuMissionPayload.builder().missionId("M-100").build())
                .build();

        when(request.getAttribute(MovuWebhookAuthFilter.EQUIPMENT_ID_ATTR)).thenReturn(equipmentId);
        when(webhookParser.parse(payload)).thenReturn(envelope);
        when(securityProps.isEnabled()).thenReturn(false);
        when(eventIngestService.ingest(any())).thenReturn(true);

        MDC.put("correlationId", "test-cid");
        try {
            ResponseEntity<Void> response = controller.handleCallback(payload, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(eventIngestService).ingest(any(MovuNotificationEnvelope.class));
        } finally {
            MDC.clear();
        }
    }

    @Test
    @DisplayName("handleCallback → invalid payload returns 400 Bad Request")
    void handleCallback_invalidPayload_returns400() {
        Map<String, Object> payload = Map.of("garbage", "data");

        when(request.getAttribute(MovuWebhookAuthFilter.EQUIPMENT_ID_ATTR)).thenReturn(null);
        when(webhookParser.parse(payload)).thenThrow(new IllegalArgumentException("Invalid payload"));

        ResponseEntity<Void> response = controller.handleCallback(payload, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(eventIngestService, never()).ingest(any());
    }

    @Test
    @DisplayName("handleCallback → unexpected exception returns 200 (prevents aSTC buffering)")
    void handleCallback_unexpectedException_returns200() {
        Map<String, Object> payload = Map.of("notificationType", "MissionDropped");

        when(request.getAttribute(MovuWebhookAuthFilter.EQUIPMENT_ID_ATTR)).thenReturn(null);
        when(webhookParser.parse(payload)).thenReturn(
                MovuNotificationEnvelope.builder().notificationType("MissionDropped").build());
        when(securityProps.isEnabled()).thenReturn(false);
        when(eventIngestService.ingest(any())).thenThrow(new RuntimeException("DB down"));

        ResponseEntity<Void> response = controller.handleCallback(payload, request);

        // CRITICAL: must return 200 even on errors — aSTC stops buffering on 200
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("handleCallback → stale timestamp returns 400")
    void handleCallback_staleTimestamp_returns400() {
        String staleTime = Instant.now().minusSeconds(600).toString(); // 10 min old
        Map<String, Object> payload = Map.of("notificationType", "MissionDropped");

        MovuNotificationEnvelope envelope = MovuNotificationEnvelope.builder()
                .notificationType("MissionDropped")
                .timeStamp(staleTime)
                .build();

        when(request.getAttribute(MovuWebhookAuthFilter.EQUIPMENT_ID_ATTR)).thenReturn(null);
        when(webhookParser.parse(payload)).thenReturn(envelope);
        when(securityProps.isEnabled()).thenReturn(true);
        when(securityProps.getTimestampMaxAgeSec()).thenReturn(300); // 5 min max

        ResponseEntity<Void> response = controller.handleCallback(payload, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(eventIngestService, never()).ingest(any());
    }

    // ── POST /callback/batch ──

    @Test
    @DisplayName("handleBatchCallback → processes all items and returns 200")
    void handleBatchCallback_allSuccess() {
        List<Map<String, Object>> batch = List.of(
                Map.of("missionId", "M-1", "status", "COMPLETED"),
                Map.of("missionId", "M-2", "status", "FAILED"));

        when(request.getAttribute(MovuWebhookAuthFilter.EQUIPMENT_ID_ATTR)).thenReturn(UUID.randomUUID());
        when(webhookParser.parse(any())).thenReturn(
                MovuNotificationEnvelope.builder().notificationType("MissionDropped").build());
        when(eventIngestService.ingest(any())).thenReturn(true);

        ResponseEntity<Void> response = controller.handleBatchCallback(batch, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(webhookParser, times(2)).parse(any());
        verify(eventIngestService, times(2)).ingest(any());
    }

    @Test
    @DisplayName("handleBatchCallback → partial failure still returns 200")
    void handleBatchCallback_partialFailure_returns200() {
        List<Map<String, Object>> batch = List.of(
                Map.of("missionId", "M-1", "status", "COMPLETED"),
                Map.of("garbage", "data"));

        when(request.getAttribute(MovuWebhookAuthFilter.EQUIPMENT_ID_ATTR)).thenReturn(null);
        when(webhookParser.parse(batch.get(0))).thenReturn(
                MovuNotificationEnvelope.builder().notificationType("MissionDropped").build());
        when(webhookParser.parse(batch.get(1))).thenThrow(new IllegalArgumentException("bad"));
        when(eventIngestService.ingest(any())).thenReturn(true);

        ResponseEntity<Void> response = controller.handleBatchCallback(batch, request);

        // Batch must always return 200 — partial success is OK
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
