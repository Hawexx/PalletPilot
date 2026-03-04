package com.palletpilot.wcs.infrastructure.web;

import com.palletpilot.wcs.domain.model.*;
import com.palletpilot.wcs.infrastructure.persistence.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.*;
import java.util.Objects;

/**
 * Operability endpoints for ERP integration monitoring, logs, and retries.
 */
@RestController
@Tag(name = "ERP Operations")
@RequestMapping("/api/erp")
@RequiredArgsConstructor
@Slf4j
public class ErpOpsController {

    private final ErpConfigRepository configRepo;
    private final ErpInboundMessageRepository inboundRepo;
    private final ErpIntegrationLogRepository logRepo;
    private final ErpOutboxMessageRepository outboxRepo;

    // ── Config ──

    @GetMapping("/config")
    public ResponseEntity<?> getConfig() {
        return ResponseEntity.ok(configRepo.findFirstByEnabledTrue()
                .map(c -> Map.of(
                        "id", c.getId().toString(),
                        "name", c.getName(),
                        "erpType", c.getErpType(),
                        "mode", c.getIntegrationMode(),
                        "enabled", c.isEnabled(),
                        "callbackUrl", c.getCallbackUrl() != null ? c.getCallbackUrl() : "",
                        "warehouseCode", c.getWarehouseCode() != null ? c.getWarehouseCode() : ""))
                .orElse(Map.of("status", "NO_CONFIG", "message", "No active ERP configuration found")));
    }

    @PostMapping("/config")
    public ResponseEntity<ErpConfig> saveConfig(@RequestBody ErpConfig config) {
        return ResponseEntity.ok(configRepo.save(config));
    }

    // ── Integration Logs ──

    @GetMapping("/logs")
    public ResponseEntity<Page<ErpIntegrationLog>> getLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(logRepo.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size)));
    }

    @GetMapping("/logs/{erpOrderId}")
    public ResponseEntity<List<ErpIntegrationLog>> getLogsByOrder(@PathVariable String erpOrderId) {
        return ResponseEntity.ok(logRepo.findByErpOrderIdOrderByCreatedAtDesc(erpOrderId));
    }

    // ── Inbound Messages ──

    @GetMapping("/inbound")
    public ResponseEntity<List<ErpInboundMessage>> getInbound(
            @RequestParam(defaultValue = "RECEIVED") String status) {
        return ResponseEntity.ok(inboundRepo.findByStatusOrderByReceivedAtDesc(status));
    }

    @PostMapping("/inbound/{id}/retry")
    public ResponseEntity<Map<String, String>> retryInbound(@PathVariable UUID id) {
        return inboundRepo.findById(id).map(msg -> {
            if ("FAILED".equals(msg.getStatus()) || "DLQ".equals(msg.getStatus())) {
                msg.setStatus("RECEIVED");
                msg.setAttemptCount(0);
                msg.setNextAttemptAt(null);
                msg.setErrorMessage(null);
                inboundRepo.save(msg);
                log.info("[ERP-OPS] Retrying inbound message: {}", msg.getMessageId());
                return ResponseEntity.ok(Map.of("status", "RETRYING", "messageId", msg.getMessageId()));
            }
            return ResponseEntity.ok(Map.of("status", "NOT_RETRYABLE",
                    "currentStatus", msg.getStatus()));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ── Outbox ──

    @GetMapping("/outbox")
    public ResponseEntity<List<ErpOutboxMessage>> getOutbox(
            @RequestParam(required = false) String status) {
        if (status != null && !status.isBlank()) {
            return ResponseEntity.ok(outboxRepo.findByStatusOrderByCreatedAtDesc(status));
        }
        return ResponseEntity.ok(outboxRepo.findAll());
    }

    @GetMapping("/outbox/{id}")
    public ResponseEntity<?> getOutboxDetail(@PathVariable UUID id) {
        return outboxRepo.findById(Objects.requireNonNull(id))
                .map(msg -> {
                    Map<String, Object> detail = new LinkedHashMap<>();
                    detail.put("id", msg.getId());
                    detail.put("idempotencyKey", msg.getIdempotencyKey());
                    detail.put("erpOrderId", msg.getErpOrderId());
                    detail.put("messageType", msg.getMessageType());
                    detail.put("status", msg.getStatus());
                    detail.put("attemptCount", msg.getAttemptCount());
                    detail.put("targetUrl", msg.getTargetUrl());
                    detail.put("requestJson", msg.getRequestJson());
                    detail.put("responseJson", msg.getResponseJson());
                    detail.put("httpStatus", msg.getHttpStatus());
                    detail.put("lastError", msg.getLastError());
                    detail.put("nextAttemptAt", msg.getNextAttemptAt());
                    detail.put("createdAt", msg.getCreatedAt());
                    detail.put("updatedAt", msg.getUpdatedAt());
                    return ResponseEntity.ok(detail);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/outbox/{id}/retry")
    public ResponseEntity<Map<String, String>> retryOutbox(@PathVariable UUID id) {
        return outboxRepo.findById(id).map(msg -> {
            if ("FAILED".equals(msg.getStatus()) || "DLQ".equals(msg.getStatus())) {
                msg.setStatus("PENDING");
                msg.setAttemptCount(0);
                msg.setNextAttemptAt(java.time.Instant.now());
                msg.setLastError(null);
                outboxRepo.save(msg);
                log.info("[ERP-OPS] Retrying outbox message: {}", msg.getId());
                return ResponseEntity.ok(Map.of("status", "RETRYING"));
            }
            return ResponseEntity.ok(Map.of("status", "NOT_RETRYABLE", "currentStatus", msg.getStatus()));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ── Inbound Detail ──

    @GetMapping("/inbound/{id}")
    public ResponseEntity<?> getInboundDetail(@PathVariable UUID id) {
        return inboundRepo.findById(id)
                .map(msg -> {
                    Map<String, Object> detail = new LinkedHashMap<>();
                    detail.put("id", msg.getId());
                    detail.put("messageId", msg.getMessageId());
                    detail.put("erpOrderId", msg.getErpOrderId());
                    detail.put("eventType", msg.getEventType());
                    detail.put("warehouseCode", msg.getWarehouseCode());
                    detail.put("correlationId", msg.getCorrelationId());
                    detail.put("status", msg.getStatus());
                    detail.put("attemptCount", msg.getAttemptCount());
                    detail.put("errorMessage", msg.getErrorMessage());
                    // Redact secret fields from payload
                    detail.put("payloadJson", msg.getPayloadJson());
                    detail.put("payloadHash", msg.getPayloadHash());
                    detail.put("wcsOrderId", msg.getWcsOrderId());
                    detail.put("receivedAt", msg.getReceivedAt());
                    detail.put("processedAt", msg.getProcessedAt());
                    detail.put("nextAttemptAt", msg.getNextAttemptAt());
                    return ResponseEntity.ok(detail);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Dashboard Stats ──

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("inbound_received", inboundRepo.countByStatus("RECEIVED"));
        stats.put("inbound_processing", inboundRepo.countByStatus("PROCESSING"));
        stats.put("inbound_processed", inboundRepo.countByStatus("PROCESSED"));
        stats.put("inbound_failed", inboundRepo.countByStatus("FAILED"));
        stats.put("inbound_dlq", inboundRepo.countByStatus("DLQ"));
        stats.put("outbox_pending", outboxRepo.countByStatus("PENDING"));
        stats.put("outbox_sent", outboxRepo.countByStatus("SENT"));
        stats.put("outbox_failed", outboxRepo.countByStatus("FAILED"));
        stats.put("outbox_dlq", outboxRepo.countByStatus("DLQ"));
        stats.put("logs_inbound_success", logRepo.countByDirectionAndStatus("INBOUND", "PROCESSED"));
        stats.put("logs_outbound_success", logRepo.countByDirectionAndStatus("OUTBOUND", "SENT"));
        return ResponseEntity.ok(stats);
    }
}
