package com.palletpilot.wcs.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

/**
 * Centralized WebSocket broadcast service.
 *
 * All real-time push notifications go through this service.
 * Topics:
 * /topic/dashboard — dashboard stats update
 * /topic/orders — order state change
 * /topic/alerts — new alert raised
 * /topic/erp — ERP inbound/outbound event
 * /topic/movu — Movu mission state change
 *
 * Usage: inject this service and call the appropriate method.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("NullableProblems")
public class WebSocketBroadcastService {

    private final SimpMessagingTemplate messagingTemplate;

    // ── Dashboard ──

    public void sendDashboardUpdate(Object stats) {
        broadcast("/topic/dashboard", "DASHBOARD_UPDATE", stats);
    }

    // ── Orders ──

    public void sendOrderUpdate(Object order) {
        broadcast("/topic/orders", "ORDER_UPDATE", order);
    }

    // ── Alerts ──

    public void sendAlert(Object alert) {
        broadcast("/topic/alerts", "ALERT", alert);
    }

    // ── ERP ──

    public void sendErpEvent(String eventType, Object payload) {
        broadcast("/topic/erp", eventType, payload);
    }

    // ── Movu ──

    public void sendMovuEvent(String eventType, Object payload) {
        broadcast("/topic/movu", eventType, payload);
    }

    // ── Generic broadcast ──

    private void broadcast(String topic, String eventType, Object payload) {
        try {
            Map<String, Object> envelope = Map.of(
                    "type", eventType,
                    "timestamp", Instant.now().toString(),
                    "data", payload);
            messagingTemplate.convertAndSend(topic, envelope);
            log.debug("[WS] Broadcast {} → {}", eventType, topic);
        } catch (Exception e) {
            // WebSocket failure must NEVER break business logic
            log.warn("[WS] Failed to broadcast {} to {}: {}", eventType, topic, e.getMessage());
        }
    }
}
