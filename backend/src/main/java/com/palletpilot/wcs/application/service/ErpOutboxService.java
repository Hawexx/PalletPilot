package com.palletpilot.wcs.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.palletpilot.wcs.domain.model.ErpOutboxMessage;
import com.palletpilot.wcs.domain.model.Order;
import com.palletpilot.wcs.infrastructure.persistence.repository.ErpOutboxMessageRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Creates outbox messages for ERP confirmations.
 * Called when an order reaches COMPLETED state.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ErpOutboxService {

    private final ErpOutboxMessageRepository outboxRepo;
    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;
    private final WebSocketBroadcastService wsBroadcast;

    @Value("${erp.callback.url:}")
    private String callbackUrl;

    @Value("${erp.auto-confirm:false}")
    private boolean autoConfirm;

    /**
     * Enqueue a shipment confirmation for ERP.
     * Idempotent: if already enqueued for this order, no-op.
     */
    @Transactional
    public void enqueueShipmentConfirmation(Order order) {
        if (!autoConfirm || callbackUrl == null || callbackUrl.isBlank()) {
            log.debug("[ERP-OUTBOX] Auto-confirm disabled or no callback URL configured");
            return;
        }

        String idempKey = "SHIPMENT_CONFIRM:" + order.getWmsOrderId();
        if (outboxRepo.existsByIdempotencyKey(idempKey)) {
            log.debug("[ERP-OUTBOX] Already enqueued for order {}", order.getWmsOrderId());
            return;
        }

        String payload = buildPayloadJson(order);

        ErpOutboxMessage msg = ErpOutboxMessage.builder()
                .idempotencyKey(idempKey)
                .erpOrderId(order.getWmsOrderId())
                .wcsOrderId(order.getId())
                .messageType("SHIPMENT_CONFIRM")
                .targetUrl(callbackUrl)
                .requestJson(payload)
                .status("PENDING")
                .nextAttemptAt(Instant.now())
                .build();

        outboxRepo.save(msg);
        log.info("[ERP-OUTBOX] Enqueued shipment confirmation for order {} → {}",
                order.getWmsOrderId(), callbackUrl);

        // Push real-time update to frontend
        wsBroadcast.sendErpEvent("SHIPMENT_ENQUEUED", Map.of(
                "orderId", order.getWmsOrderId(),
                "status", "PENDING"));
    }

    /**
     * Build the JSON payload using ObjectMapper for safe serialization.
     * Handles special characters in ERP order IDs correctly.
     */
    private String buildPayloadJson(Order order) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("erpOrderId", order.getWmsOrderId());
            payload.put("wcsOrderId", order.getId());
            payload.put("status", "COMPLETED");
            payload.put("completedAt", Instant.now().toString());
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            // Fallback: should never happen with simple Map, but handle gracefully
            log.error("[ERP-OUTBOX] Failed to serialize payload for order {}: {}",
                    order.getWmsOrderId(), e.getMessage());
            throw new IllegalStateException("Failed to serialize ERP outbox payload", e);
        }
    }
}
