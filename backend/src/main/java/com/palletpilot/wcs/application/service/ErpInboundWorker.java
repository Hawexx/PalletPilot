package com.palletpilot.wcs.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.palletpilot.wcs.application.dto.ErpOrderEventPayload;
import com.palletpilot.wcs.domain.model.ErpInboundMessage;
import com.palletpilot.wcs.domain.model.ErpIntegrationLog;
import com.palletpilot.wcs.domain.model.Order;
import com.palletpilot.wcs.domain.model.OrderItem;
import com.palletpilot.wcs.domain.model.enums.ProcessState;
import com.palletpilot.wcs.infrastructure.persistence.repository.ErpInboundMessageRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.ErpIntegrationLogRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Async worker that processes inbound ERP messages.
 * Polls DB for RECEIVED/FAILED messages and processes them:
 * - ORDER_CREATE → create Order
 * - ORDER_UPDATE → update existing Order (if not started)
 * - ORDER_CANCEL → cancel Order (release slots if needed)
 *
 * Uses PESSIMISTIC_WRITE (FOR UPDATE SKIP LOCKED) for concurrency safety.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ErpInboundWorker {

    private final ErpInboundMessageRepository inboundRepo;
    private final ErpIntegrationLogRepository logRepo;
    private final OrderRepository orderRepository;
    private final ErpOutboxService erpOutboxService;
    private final ObjectMapper objectMapper;

    @Value("${erp.inbound.worker.enabled:true}")
    private boolean workerEnabled;

    @Value("${erp.inbound.worker.batch-size:20}")
    private int batchSize;

    /**
     * Scheduled poll: process pending inbound messages.
     * Runs every 2 seconds.
     */
    @Scheduled(fixedDelayString = "${erp.inbound.worker.poll-ms:2000}")
    @Transactional
    public void pollAndProcess() {
        if (!workerEnabled)
            return;

        List<ErpInboundMessage> messages = inboundRepo.findReadyForProcessing(Instant.now(), batchSize);
        if (messages.isEmpty())
            return;

        log.info("[ERP-WORKER] Processing {} inbound messages", messages.size());

        for (ErpInboundMessage msg : messages) {
            processMessage(msg);
        }
    }

    @Transactional
    public void processMessage(ErpInboundMessage msg) {
        String corrId = msg.getCorrelationId();
        MDC.put("correlationId", corrId);
        MDC.put("erpOrderId", msg.getErpOrderId());
        MDC.put("messageId", msg.getMessageId());
        log.info("[ERP-WORKER] Processing message: id={}, erpOrder={}, type={}, corrId={}",
                msg.getMessageId(), msg.getErpOrderId(), msg.getEventType(), corrId);

        msg.setStatus("PROCESSING");
        msg.setAttemptCount(msg.getAttemptCount() + 1);
        inboundRepo.save(msg);

        try {
            ErpOrderEventPayload payload = objectMapper.readValue(
                    msg.getPayloadJson(), ErpOrderEventPayload.class);

            switch (msg.getEventType()) {
                case "ORDER_CREATE" -> handleCreate(msg, payload);
                case "ORDER_UPDATE" -> handleUpdate(msg, payload);
                case "ORDER_CANCEL" -> handleCancel(msg, payload);
                default -> {
                    msg.setStatus("FAILED");
                    msg.setErrorMessage("Unknown eventType: " + msg.getEventType());
                }
            }

            if (!"FAILED".equals(msg.getStatus())) {
                msg.setStatus("PROCESSED");
            }
            msg.setProcessedAt(Instant.now());

        } catch (Exception e) {
            log.error("[ERP-WORKER] Processing failed for message {}: {}",
                    msg.getMessageId(), e.getMessage(), e);
            msg.setStatus("FAILED");
            msg.setErrorMessage(e.getMessage());
            // Exponential backoff for retry
            int delay = Math.min(60, (int) Math.pow(2, msg.getAttemptCount()));
            msg.setNextAttemptAt(Instant.now().plusSeconds(delay));

            if (msg.getAttemptCount() >= 5) {
                msg.setStatus("DLQ");
                log.error("[ERP-WORKER] Message {} moved to DLQ after {} attempts",
                        msg.getMessageId(), msg.getAttemptCount());
            }
        }

        inboundRepo.save(msg);

        // Log integration event
        try {
            logRepo.save(ErpIntegrationLog.builder()
                    .direction("INBOUND")
                    .eventType(msg.getEventType())
                    .erpOrderId(msg.getErpOrderId())
                    .wcsOrderId(msg.getWcsOrderId())
                    .correlationId(corrId)
                    .requestJson(msg.getPayloadJson())
                    .status(msg.getStatus())
                    .errorMessage(msg.getErrorMessage())
                    .createdAt(Instant.now())
                    .processedAt(Instant.now())
                    .build());
        } finally {
            MDC.remove("correlationId");
            MDC.remove("erpOrderId");
            MDC.remove("messageId");
        }
    }

    private void handleCreate(ErpInboundMessage msg, ErpOrderEventPayload payload) {
        // Dedupe: check if order already exists
        Optional<Order> existing = orderRepository.findByWmsOrderId(payload.erpOrderId());
        if (existing.isPresent()) {
            log.info("[ERP-WORKER] Order already exists for erpOrderId={}, returning existing",
                    payload.erpOrderId());
            msg.setWcsOrderId(existing.get().getId());
            return; // idempotent, not an error
        }

        // Validate lines
        if (payload.lines() == null || payload.lines().isEmpty()) {
            msg.setStatus("FAILED");
            msg.setErrorMessage("ORDER_CREATE requires at least one order line");
            return;
        }

        // Build order
        Order order = Order.builder()
                .wmsOrderId(payload.erpOrderId())
                .priority(payload.priority() != null ? payload.priority() : 5)
                .status(ProcessState.CREATED)
                .items(new ArrayList<>())
                .build();

        List<OrderItem> items = payload.lines().stream()
                .map(line -> {
                    // Enrichment: use defaults if dims/weight missing
                    int width = line.widthMm() != null ? line.widthMm() : 300;
                    int length = line.lengthMm() != null ? line.lengthMm() : 250;
                    int height = line.heightMm() != null ? line.heightMm() : 200;
                    double weight = line.weightKg() != null ? line.weightKg() : 1.0;

                    if (line.widthMm() == null || line.heightMm() == null) {
                        log.warn("[ERP-WORKER] Missing dimensions for SKU={}, using defaults " +
                                "({}x{}x{}, {}kg)", line.sku(), width, length, height, weight);
                    }

                    return OrderItem.builder()
                            .order(order)
                            .sku(line.sku())
                            .widthMm(width)
                            .lengthMm(length)
                            .heightMm(height)
                            .weightKg(weight)
                            .quantity(line.quantity())
                            .build();
                })
                .collect(Collectors.toList());

        order.setItems(items);
        Order saved = orderRepository.save(order);
        msg.setWcsOrderId(saved.getId());

        log.info("[ERP-WORKER] Created order: wcsId={}, erpId={}, items={}",
                saved.getId(), payload.erpOrderId(), items.size());

        // Enqueue ERP confirmation (guarded by erp.auto-confirm flag + callback URL)
        try {
            erpOutboxService.enqueueShipmentConfirmation(saved);
        } catch (Exception e) {
            log.warn("[ERP-WORKER] Outbox enqueue failed for order {} — non-blocking: {}",
                    saved.getId(), e.getMessage());
        }
    }

    private void handleUpdate(ErpInboundMessage msg, ErpOrderEventPayload payload) {
        Optional<Order> existing = orderRepository.findByWmsOrderId(payload.erpOrderId());
        if (existing.isEmpty()) {
            // Policy: create if not exists (some ERPs send UPDATE as first event)
            log.info("[ERP-WORKER] Order not found for UPDATE, creating: {}", payload.erpOrderId());
            handleCreate(msg, payload);
            return;
        }

        Order order = existing.get();
        msg.setWcsOrderId(order.getId());

        // Cannot update if already in execution
        if (order.getStatus() != ProcessState.CREATED && order.getStatus() != ProcessState.PLANNED) {
            msg.setStatus("FAILED");
            msg.setErrorMessage("UPDATE_NOT_ALLOWED: order is in state " + order.getStatus());
            log.warn("[ERP-WORKER] Cannot update order {} in state {}",
                    payload.erpOrderId(), order.getStatus());
            return;
        }

        // Update priority
        if (payload.priority() != null) {
            order.setPriority(payload.priority());
        }

        // Update lines if provided
        if (payload.lines() != null && !payload.lines().isEmpty()) {
            order.getItems().clear();
            List<OrderItem> newItems = payload.lines().stream()
                    .map(line -> OrderItem.builder()
                            .order(order)
                            .sku(line.sku())
                            .widthMm(line.widthMm() != null ? line.widthMm() : 300)
                            .lengthMm(line.lengthMm() != null ? line.lengthMm() : 250)
                            .heightMm(line.heightMm() != null ? line.heightMm() : 200)
                            .weightKg(line.weightKg() != null ? line.weightKg() : 1.0)
                            .quantity(line.quantity())
                            .build())
                    .collect(Collectors.toList());
            order.getItems().addAll(newItems);
        }

        orderRepository.save(order);
        log.info("[ERP-WORKER] Updated order: {}", payload.erpOrderId());
    }

    private void handleCancel(ErpInboundMessage msg, ErpOrderEventPayload payload) {
        Optional<Order> existing = orderRepository.findByWmsOrderId(payload.erpOrderId());
        if (existing.isEmpty()) {
            log.info("[ERP-WORKER] Order not found for CANCEL: {} (no-op)", payload.erpOrderId());
            return;
        }

        Order order = existing.get();
        msg.setWcsOrderId(order.getId());
        ProcessState previousState = order.getStatus();

        if (previousState == ProcessState.COMPLETED) {
            msg.setStatus("FAILED");
            msg.setErrorMessage("CANCEL_NOT_ALLOWED: order already COMPLETED");
            return;
        }

        if (previousState == ProcessState.CANCELLED || previousState == ProcessState.CANCEL_REQUESTED) {
            log.info("[ERP-WORKER] Order {} already cancelled/cancel-requested (no-op)", payload.erpOrderId());
            return;
        }

        // Not-started orders: cancel immediately
        if (previousState == ProcessState.CREATED || previousState == ProcessState.PLANNED) {
            order.setStatus(ProcessState.CANCELLED);
            orderRepository.save(order);
            log.info("[ERP-WORKER] Cancelled order: {} (was {})", payload.erpOrderId(), previousState);
        } else {
            // In-flight orders: mark as CANCEL_REQUESTED (tasks need safe abort)
            order.setStatus(ProcessState.CANCEL_REQUESTED);
            orderRepository.save(order);
            log.warn("[ERP-WORKER] Order {} set to CANCEL_REQUESTED (was {}). " +
                    "In-flight tasks require operator attention.",
                    payload.erpOrderId(), previousState);
        }
    }
}
