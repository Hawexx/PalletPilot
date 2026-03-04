package com.palletpilot.wcs.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.palletpilot.wcs.application.dto.ErpIngestResponse;
import com.palletpilot.wcs.domain.model.ErpInboundMessage;
import com.palletpilot.wcs.infrastructure.erp.ErpHmacSigner;
import com.palletpilot.wcs.infrastructure.persistence.repository.ErpInboundMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ErpWebhookController.
 * Tests the full ingest pipeline: feature toggle, HMAC, timestamp replay guard,
 * JSON validation, idempotency, and batch processing.
 */
@ExtendWith(MockitoExtension.class)
class ErpWebhookControllerTest {

    @Mock
    private ErpInboundMessageRepository inboundRepo;

    @Mock
    private ErpHmacSigner hmacSigner;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ErpWebhookController controller;

    @BeforeEach
    void setUp() {
        // Inject @Value fields via reflection (pure unit test, no Spring context)
        ReflectionTestUtils.setField(controller, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(controller, "erpEnabled", true);
        ReflectionTestUtils.setField(controller, "webhookEnabled", true);
        ReflectionTestUtils.setField(controller, "webhookSecret", "");
        ReflectionTestUtils.setField(controller, "timestampMaxAge", 300);
        ReflectionTestUtils.setField(controller, "maxBatchSize", 100);
    }

    // ── Feature Toggle ──

    @Test
    @DisplayName("ingest → 503 when ERP is disabled")
    void ingest_erpDisabled_returns503() {
        ReflectionTestUtils.setField(controller, "erpEnabled", false);

        ResponseEntity<?> response = controller.ingest("{}", null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("ingest → 503 when webhook is disabled")
    void ingest_webhookDisabled_returns503() {
        ReflectionTestUtils.setField(controller, "webhookEnabled", false);

        ResponseEntity<?> response = controller.ingest("{}", null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    // ── HMAC Verification ──

    @Test
    @DisplayName("ingest → 400 when HMAC configured but no timestamp header")
    void ingest_hmacEnabled_missingTimestamp_returns400() {
        ReflectionTestUtils.setField(controller, "webhookSecret", "secret123");

        ResponseEntity<?> response = controller.ingest("{}", "sig", null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("ingest → 400 on stale timestamp (replay attack)")
    void ingest_staleTimestamp_returns400() {
        ReflectionTestUtils.setField(controller, "webhookSecret", "secret123");
        String staleTs = String.valueOf(Instant.now().getEpochSecond() - 600); // 10 min old

        ResponseEntity<?> response = controller.ingest("{}", "sig", staleTs, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("ingest → 401 on invalid HMAC signature")
    void ingest_invalidSignature_returns401() {
        ReflectionTestUtils.setField(controller, "webhookSecret", "secret123");
        String validTs = String.valueOf(Instant.now().getEpochSecond());
        when(hmacSigner.verify(any(), any(), any())).thenReturn(false);

        ResponseEntity<?> response = controller.ingest("{}", "bad-sig", validTs, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── JSON Validation ──

    @Test
    @DisplayName("ingest → 400 on invalid JSON body")
    void ingest_invalidJson_returns400() {
        ResponseEntity<?> response = controller.ingest("not-json", null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("ingest → 400 on missing erpOrderId")
    void ingest_missingErpOrderId_returns400() {
        String body = "{\"eventType\":\"ORDER_CREATE\"}";

        ResponseEntity<?> response = controller.ingest(body, null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── Idempotency ──

    @Test
    @DisplayName("ingest → 202 Accepted on new message")
    void ingest_newMessage_returns202() {
        String body = "{\"eventType\":\"ORDER_CREATE\",\"erpOrderId\":\"ORD-001\"}";
        when(hmacSigner.sha256(anyString())).thenReturn("hash123");
        when(inboundRepo.findByMessageId(anyString())).thenReturn(Optional.empty());
        when(inboundRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        ResponseEntity<?> response = controller.ingest(body, null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(inboundRepo).save(any(ErpInboundMessage.class));
    }

    @Test
    @DisplayName("ingest → 200 DUPLICATE on already-seen messageId")
    void ingest_duplicateMessage_returns200() {
        String body = "{\"eventType\":\"ORDER_CREATE\",\"erpOrderId\":\"ORD-001\"}";
        when(hmacSigner.sha256(anyString())).thenReturn("hash123");
        when(inboundRepo.findByMessageId(anyString())).thenReturn(
                Optional.of(ErpInboundMessage.builder().messageId("msg-1").status("PROCESSED").build()));

        ResponseEntity<?> response = controller.ingest(body, null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(inboundRepo, never()).save(any());
    }

    // ── Batch ──

    @Test
    @DisplayName("ingestBatch → 413 when batch exceeds max size")
    void ingestBatch_exceedsLimit_returns413() throws Exception {
        ReflectionTestUtils.setField(controller, "maxBatchSize", 2);

        // Build array of 3 items (exceeds limit of 2)
        String body = "[{\"eventType\":\"ORDER_CREATE\",\"erpOrderId\":\"O-1\"}," +
                "{\"eventType\":\"ORDER_CREATE\",\"erpOrderId\":\"O-2\"}," +
                "{\"eventType\":\"ORDER_CREATE\",\"erpOrderId\":\"O-3\"}]";

        ResponseEntity<?> response = controller.ingestBatch(body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @Test
    @DisplayName("ingestBatch → 202 with correct counts on mixed batch")
    @SuppressWarnings("unchecked")
    void ingestBatch_mixedBatch_returnsCorrectCounts() throws Exception {
        String body = "[{\"eventType\":\"ORDER_CREATE\",\"erpOrderId\":\"O-1\"}," +
                "{\"eventType\":\"ORDER_UPDATE\",\"erpOrderId\":\"O-2\"}]";

        when(hmacSigner.sha256(anyString())).thenReturn("hash1", "hash2", "key1", "key2");
        when(inboundRepo.existsByMessageId(anyString())).thenReturn(false, true); // first new, second duplicate
        when(inboundRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        ResponseEntity<?> response = controller.ingestBatch(body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        Map<String, Object> result = (Map<String, Object>) response.getBody();
        assertThat(result).isNotNull();
        assertThat(result.get("totalCount")).isEqualTo(2);
    }
}
