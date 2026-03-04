package com.palletpilot.wcs.infrastructure.web;

import com.palletpilot.wcs.domain.model.ErpConfig;
import com.palletpilot.wcs.domain.model.ErpInboundMessage;
import com.palletpilot.wcs.domain.model.ErpOutboxMessage;
import com.palletpilot.wcs.infrastructure.persistence.repository.ErpConfigRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.ErpInboundMessageRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.ErpIntegrationLogRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.ErpOutboxMessageRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ErpOpsController — ERP integration monitoring, retry, and
 * stats.
 */
@ExtendWith(MockitoExtension.class)
class ErpOpsControllerTest {

    @Mock
    private ErpConfigRepository configRepo;
    @Mock
    private ErpInboundMessageRepository inboundRepo;
    @Mock
    private ErpIntegrationLogRepository logRepo;
    @Mock
    private ErpOutboxMessageRepository outboxRepo;
    @InjectMocks
    private ErpOpsController controller;

    // ── Config ──

    @Test
    @DisplayName("getConfig: active config → returns mapped fields")
    void getConfig_activeConfig_returnsMappedFields() {
        ErpConfig cfg = ErpConfig.builder()
                .id(UUID.randomUUID())
                .name("SAP")
                .erpType("SAP_S4")
                .integrationMode("WEBHOOK")
                .enabled(true)
                .callbackUrl("https://erp.example.com/callback")
                .warehouseCode("WH-01")
                .build();
        when(configRepo.findFirstByEnabledTrue()).thenReturn(Optional.of(cfg));

        var resp = controller.getConfig();

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("getConfig: no config → returns NO_CONFIG message")
    void getConfig_noConfig_returnsNoConfigMessage() {
        when(configRepo.findFirstByEnabledTrue()).thenReturn(Optional.empty());

        var resp = controller.getConfig();

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("saveConfig → saves and returns")
    void saveConfig_savesAndReturns() {
        ErpConfig cfg = ErpConfig.builder().name("TEST").build();
        when(configRepo.save(cfg)).thenReturn(cfg);

        var resp = controller.saveConfig(cfg);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(configRepo).save(cfg);
    }

    // ── Inbound Retry ──

    @Test
    @DisplayName("retryInbound: FAILED message → resets to RECEIVED")
    void retryInbound_failed_resetsToReceived() {
        UUID id = UUID.randomUUID();
        ErpInboundMessage msg = ErpInboundMessage.builder()
                .id(id).messageId("MSG-001").status("FAILED").attemptCount(3).build();
        when(inboundRepo.findById(id)).thenReturn(Optional.of(msg));

        var resp = controller.retryInbound(id);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(msg.getStatus()).isEqualTo("RECEIVED");
        assertThat(msg.getAttemptCount()).isEqualTo(0);
        verify(inboundRepo).save(msg);
    }

    @Test
    @DisplayName("retryInbound: DLQ message → also retryable")
    void retryInbound_dlq_retryable() {
        UUID id = UUID.randomUUID();
        ErpInboundMessage msg = ErpInboundMessage.builder()
                .id(id).messageId("MSG-002").status("DLQ").build();
        when(inboundRepo.findById(id)).thenReturn(Optional.of(msg));

        var resp = controller.retryInbound(id);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(msg.getStatus()).isEqualTo("RECEIVED");
    }

    @Test
    @DisplayName("retryInbound: PROCESSING message → NOT_RETRYABLE")
    void retryInbound_processing_notRetryable() {
        UUID id = UUID.randomUUID();
        ErpInboundMessage msg = ErpInboundMessage.builder()
                .id(id).messageId("MSG-003").status("PROCESSING").build();
        when(inboundRepo.findById(id)).thenReturn(Optional.of(msg));

        var resp = controller.retryInbound(id);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(inboundRepo, never()).save(any());
    }

    @Test
    @DisplayName("retryInbound: not found → 404")
    void retryInbound_notFound_404() {
        UUID id = UUID.randomUUID();
        when(inboundRepo.findById(id)).thenReturn(Optional.empty());

        var resp = controller.retryInbound(id);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── Outbox Retry ──

    @Test
    @DisplayName("retryOutbox: FAILED → resets to PENDING")
    void retryOutbox_failed_resetsToPending() {
        UUID id = UUID.randomUUID();
        ErpOutboxMessage msg = ErpOutboxMessage.builder()
                .id(id).status("FAILED").attemptCount(5).build();
        when(outboxRepo.findById(id)).thenReturn(Optional.of(msg));

        var resp = controller.retryOutbox(id);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(msg.getStatus()).isEqualTo("PENDING");
        assertThat(msg.getAttemptCount()).isEqualTo(0);
        verify(outboxRepo).save(msg);
    }

    @Test
    @DisplayName("retryOutbox: not found → 404")
    void retryOutbox_notFound_404() {
        UUID id = UUID.randomUUID();
        when(outboxRepo.findById(id)).thenReturn(Optional.empty());

        var resp = controller.retryOutbox(id);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── Outbox List ──

    @Test
    @DisplayName("getOutbox: with status → filters by status")
    void getOutbox_withStatus_filters() {
        when(outboxRepo.findByStatusOrderByCreatedAtDesc("PENDING")).thenReturn(List.of());

        controller.getOutbox("PENDING");

        verify(outboxRepo).findByStatusOrderByCreatedAtDesc("PENDING");
        verify(outboxRepo, never()).findAll();
    }

    @Test
    @DisplayName("getOutbox: null status → returns all")
    void getOutbox_nullStatus_returnsAll() {
        when(outboxRepo.findAll()).thenReturn(List.of());

        controller.getOutbox(null);

        verify(outboxRepo).findAll();
    }

    // ── Stats ──

    @Test
    @DisplayName("getStats → aggregates all status counts")
    void getStats_aggregatesAllCounts() {
        when(inboundRepo.countByStatus(anyString())).thenReturn(1L);
        when(outboxRepo.countByStatus(anyString())).thenReturn(2L);
        when(logRepo.countByDirectionAndStatus(anyString(), anyString())).thenReturn(3L);

        var resp = controller.getStats();

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        // Verify all 11 stat keys are present
        assertThat(resp.getBody()).containsKeys(
                "inbound_received", "inbound_processing", "inbound_processed",
                "inbound_failed", "inbound_dlq",
                "outbox_pending", "outbox_sent", "outbox_failed", "outbox_dlq",
                "logs_inbound_success", "logs_outbound_success");
    }
}
