package com.palletpilot.wcs.infrastructure.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.palletpilot.wcs.application.dto.ErpIngestResponse;
import com.palletpilot.wcs.application.dto.ErpOrderEventPayload;
import com.palletpilot.wcs.domain.model.ErpInboundMessage;
import com.palletpilot.wcs.infrastructure.erp.ErpHmacSigner;
import com.palletpilot.wcs.infrastructure.persistence.repository.ErpInboundMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.Instant;
import java.util.*;

/**
 * Webhook endpoint for ERP order ingestion.
 * Design: validate → persist → fast 202 Accepted.
 * Processing happens asynchronously via ErpInboundWorker.
 */
@RestController
@Tag(name = "ERP Webhooks")
@RequestMapping("/api/erp")
@RequiredArgsConstructor
@Slf4j
public class ErpWebhookController {

    private final ErpInboundMessageRepository inboundRepo;
    private final ErpHmacSigner hmacSigner;
    private final ObjectMapper objectMapper;

    @Value("${erp.enabled:false}")
    private boolean erpEnabled;

    @Value("${erp.webhook.enabled:false}")
    private boolean webhookEnabled;

    @Value("${erp.webhook.secret:}")
    private String webhookSecret;

    @Value("${erp.webhook.timestamp-max-age-seconds:300}")
    private int timestampMaxAge;

    @Value("${erp.webhook.max-batch-size:100}")
    private int maxBatchSize;

    /**
     * POST /api/erp/ingest
     * Fast ingest: validate minimally, persist to DB, return 202.
     * Worker processes the message asynchronously.
     */
    @PostMapping("/ingest")
    public ResponseEntity<?> ingest(@RequestBody String rawBody,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature,
            @RequestHeader(value = "X-Webhook-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {

        if (!erpEnabled || !webhookEnabled) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "ERP webhook is disabled"));
        }

        // ── HMAC Verification (if secret configured) ──
        if (webhookSecret != null && !webhookSecret.isEmpty()) {
            // Timestamp replay guard — REQUIRED when HMAC is configured
            if (timestamp == null || timestamp.isBlank()) {
                log.warn("[ERP] Missing X-Webhook-Timestamp header");
                return ResponseEntity.badRequest().body(Map.of("error", "Missing timestamp header"));
            }
            try {
                long ts = Long.parseLong(timestamp);
                long now = Instant.now().getEpochSecond();
                if (Math.abs(now - ts) > timestampMaxAge) {
                    log.warn("[ERP] Stale timestamp: {} (now={})", ts, now);
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Map.of("error", "Stale timestamp"));
                }
            } catch (NumberFormatException e) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid timestamp format"));
            }

            // HMAC signature check
            if (signature == null || !hmacSigner.verify(
                    rawBody.getBytes(), webhookSecret, signature)) {
                log.warn("[ERP] Invalid HMAC signature");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Invalid signature"));
            }
        }

        // ── Parse payload ──
        ErpOrderEventPayload payload;
        try {
            payload = objectMapper.readValue(rawBody, ErpOrderEventPayload.class);
        } catch (JsonProcessingException e) {
            log.warn("[ERP] Invalid JSON payload: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ErpIngestResponse.invalid(null, "Invalid JSON: " + e.getMessage()));
        }

        // ── Basic validation ──
        if (payload.erpOrderId() == null || payload.erpOrderId().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ErpIngestResponse.invalid(null, "erpOrderId is required"));
        }
        if (payload.eventType() == null || payload.eventType().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ErpIngestResponse.invalid(null, "eventType is required"));
        }

        // ── Generate correlation + message IDs ──
        String corrId = (correlationId != null && !correlationId.isBlank())
                ? correlationId
                : "erp-" + UUID.randomUUID();

        String payloadHash = hmacSigner.sha256(rawBody);

        // Deterministic messageId if not provided by ERP
        String msgId = payload.messageId();
        if (msgId == null || msgId.isBlank()) {
            msgId = hmacSigner.sha256(
                    (payload.erpSystem() != null ? payload.erpSystem() : "UNKNOWN")
                            + ":" + payload.erpOrderId()
                            + ":" + payload.eventType()
                            + ":" + payloadHash);
        }

        // ── Idempotency: check if message already exists ──
        Optional<ErpInboundMessage> existing = inboundRepo.findByMessageId(msgId);
        if (existing.isPresent()) {
            ErpInboundMessage ex = existing.get();
            log.info("[ERP] Duplicate message: {} (status={})", msgId, ex.getStatus());
            return ResponseEntity.ok(
                    ErpIngestResponse.duplicate(msgId, payload.erpOrderId(),
                            ex.getWcsOrderId() != null ? ex.getWcsOrderId().toString() : null));
        }

        // ── Persist inbound message ──
        ErpInboundMessage msg = ErpInboundMessage.builder()
                .messageId(msgId)
                .erpOrderId(payload.erpOrderId())
                .eventType(payload.eventType())
                .warehouseCode(payload.warehouseCode())
                .correlationId(corrId)
                .payloadJson(rawBody)
                .payloadHash(payloadHash)
                .status("RECEIVED")
                .receivedAt(Instant.now())
                .build();

        try {
            inboundRepo.save(msg);
        } catch (DataIntegrityViolationException e) {
            // Race condition: another request saved same messageId
            log.info("[ERP] Concurrent duplicate for messageId: {}", msgId);
            return ResponseEntity.ok(
                    ErpIngestResponse.duplicate(msgId, payload.erpOrderId(), null));
        }

        log.info("[ERP] Ingested message: id={}, erpOrder={}, type={}, corrId={}",
                msgId, payload.erpOrderId(), payload.eventType(), corrId);

        // ── Fast response: 202 Accepted ──
        return ResponseEntity.accepted()
                .body(ErpIngestResponse.received(msgId, payload.erpOrderId()));
    }

    /**
     * POST /api/erp/ingest/batch
     * Accepts array of payloads, persists each, returns summary.
     */
    @PostMapping("/ingest/batch")
    public ResponseEntity<?> ingestBatch(@RequestBody String rawBody) {
        if (!erpEnabled || !webhookEnabled) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "ERP webhook is disabled"));
        }

        ErpOrderEventPayload[] payloads;
        try {
            payloads = objectMapper.readValue(rawBody, ErpOrderEventPayload[].class);
        } catch (JsonProcessingException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid JSON array: " + e.getMessage()));
        }

        if (payloads.length > maxBatchSize) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(Map.of("error", "Batch size exceeds limit of " + maxBatchSize,
                            "maxBatchSize", maxBatchSize, "receivedSize", payloads.length));
        }

        int accepted = 0, duplicates = 0, failed = 0;

        for (ErpOrderEventPayload p : payloads) {
            try {
                String itemJson = objectMapper.writeValueAsString(p);
                String hash = hmacSigner.sha256(itemJson);
                String msgId = p.messageId() != null ? p.messageId()
                        : hmacSigner.sha256((p.erpSystem() != null ? p.erpSystem() : "")
                                + ":" + p.erpOrderId() + ":" + p.eventType() + ":" + hash);

                if (inboundRepo.existsByMessageId(msgId)) {
                    duplicates++;
                    continue;
                }

                inboundRepo.save(ErpInboundMessage.builder()
                        .messageId(msgId)
                        .erpOrderId(p.erpOrderId())
                        .eventType(p.eventType())
                        .warehouseCode(p.warehouseCode())
                        .correlationId("erp-batch-" + UUID.randomUUID())
                        .payloadJson(itemJson)
                        .payloadHash(hash)
                        .status("RECEIVED")
                        .receivedAt(Instant.now())
                        .build());
                accepted++;
            } catch (Exception e) {
                log.warn("[ERP] Batch item failed: {}", e.getMessage());
                failed++;
            }
        }

        return ResponseEntity.accepted().body(Map.of(
                "acceptedCount", accepted,
                "duplicateCount", duplicates,
                "failedCount", failed,
                "totalCount", payloads.length));
    }
}
