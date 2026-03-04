package com.palletpilot.wcs.infrastructure.movu;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.palletpilot.wcs.infrastructure.movu.dto.MovuMissionPayload;
import com.palletpilot.wcs.infrastructure.movu.dto.MovuNotificationEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Parses incoming Movu webhook payloads into typed DTOs.
 * Supports two formats:
 * 
 * 1. Envelope format (real aSTC):
 * { "notificationType": "MissionDropped", "payload": {...}, "notificationId":
 * "...", ... }
 * 
 * 2. Legacy flat format (old PalletPilot MVP):
 * { "missionId": "...", "status": "COMPLETED" }
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MovuWebhookParser {

    private final ObjectMapper objectMapper;

    /**
     * Parse a raw webhook payload (Map) into a typed MovuNotificationEnvelope.
     * Auto-detects envelope vs legacy flat format.
     *
     * @param rawPayload The raw Map from the request body
     * @return Typed envelope, never null
     * @throws IllegalArgumentException if payload cannot be parsed at all
     */
    public MovuNotificationEnvelope parse(Map<String, Object> rawPayload) {
        if (rawPayload == null || rawPayload.isEmpty()) {
            throw new IllegalArgumentException("Empty webhook payload");
        }

        // Detect format: envelope has "notificationType", legacy has "missionId" +
        // "status"
        boolean isEnvelope = rawPayload.containsKey("notificationType")
                || rawPayload.containsKey("NotificationType");

        if (isEnvelope) {
            return parseEnvelopeFormat(rawPayload);
        } else {
            return parseLegacyFlatFormat(rawPayload);
        }
    }

    /**
     * Parse the real Movu aSTC envelope format.
     * Handles both camelCase (v1.2+) and PascalCase (v1.0) keys.
     */
    private MovuNotificationEnvelope parseEnvelopeFormat(Map<String, Object> rawPayload) {
        String notificationType = getStringField(rawPayload, "notificationType", "NotificationType");
        String notificationId = getStringField(rawPayload, "notificationId", "NotificationId");
        String timeStamp = getStringField(rawPayload, "timeStamp", "TimeStamp", "timestamp");
        String contextType = getStringField(rawPayload, "contextType", "ContextType");

        // Parse the nested payload into MovuMissionPayload
        Object payloadObj = rawPayload.getOrDefault("payload",
                rawPayload.getOrDefault("Payload", null));

        MovuMissionPayload missionPayload = parsePayloadObject(payloadObj);

        // Generate notificationId if missing (shouldn't happen with real aSTC but
        // safety)
        if (notificationId == null || notificationId.isBlank()) {
            notificationId = "generated-" + UUID.randomUUID();
            log.warn("Webhook payload missing notificationId, generated: {}", notificationId);
        }

        return MovuNotificationEnvelope.builder()
                .notificationType(notificationType)
                .notificationId(notificationId)
                .timeStamp(timeStamp)
                .contextType(contextType)
                .missionPayload(missionPayload)
                .legacyFormat(false)
                .build();
    }

    /**
     * Parse the legacy PalletPilot flat format for backward compatibility.
     * Converts { "missionId": "x", "status": "COMPLETED" } into an envelope.
     */
    private MovuNotificationEnvelope parseLegacyFlatFormat(Map<String, Object> rawPayload) {
        String missionId = getStringField(rawPayload, "missionId", "MissionId");
        String status = getStringField(rawPayload, "status", "Status");

        if (missionId == null || status == null) {
            throw new IllegalArgumentException(
                    "Legacy payload must contain 'missionId' and 'status'. Got: " + rawPayload.keySet());
        }

        // Map legacy status string to notificationType
        String notificationType = mapLegacyStatusToNotificationType(status);

        MovuMissionPayload payload = MovuMissionPayload.builder()
                .missionId(missionId)
                .build();

        return MovuNotificationEnvelope.builder()
                .notificationType(notificationType)
                .notificationId("legacy-" + UUID.randomUUID()) // Generate unique ID
                .timeStamp(java.time.Instant.now().toString())
                .contextType("Mission")
                .missionPayload(payload)
                .legacyFormat(true)
                .build();
    }

    /**
     * Parse the nested "payload" object from the envelope into a typed DTO.
     */
    @SuppressWarnings("unchecked")
    private MovuMissionPayload parsePayloadObject(Object payloadObj) {
        if (payloadObj == null) {
            return MovuMissionPayload.builder().build();
        }

        try {
            // Convert via ObjectMapper for @JsonAlias support (handles
            // PascalCase/camelCase)
            JsonNode payloadNode = objectMapper.valueToTree(payloadObj);
            MovuMissionPayload parsed = objectMapper.treeToValue(payloadNode, MovuMissionPayload.class);

            // Normalize missionId: Movu sometimes sends as integer, we want string
            if (parsed.getMissionId() != null) {
                parsed.setMissionId(parsed.getMissionId().trim());
            }

            return parsed;
        } catch (JsonProcessingException e) {
            log.error("Failed to parse webhook payload object: {}", payloadObj, e);
            return MovuMissionPayload.builder().build();
        }
    }

    /**
     * Map a legacy flat status string to Movu notificationType.
     */
    private String mapLegacyStatusToNotificationType(String status) {
        return switch (status.toUpperCase()) {
            case "COMPLETED", "DROPPED" -> "MissionDropped";
            case "FAILED", "ERROR" -> "MissionError";
            case "RUNNING", "ASSIGNED" -> "MissionAssigned";
            case "PICKED" -> "MissionPicked";
            case "CANCELLED" -> "MissionCancelled";
            case "ABORTED" -> "MissionAborted";
            case "CREATED" -> "MissionCreated";
            default -> "MissionAssigned"; // Safe fallback for RUNNING-like states
        };
    }

    /**
     * Get a string field by trying multiple key variants (camelCase + PascalCase).
     */
    private String getStringField(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object val = map.get(key);
            if (val != null) {
                return val.toString();
            }
        }
        return null;
    }
}
