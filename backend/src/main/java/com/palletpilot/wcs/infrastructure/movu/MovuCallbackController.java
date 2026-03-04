package com.palletpilot.wcs.infrastructure.movu;

import com.palletpilot.wcs.application.service.MovuEventIngestService;
import com.palletpilot.wcs.infrastructure.movu.config.MovuSecurityProperties;
import com.palletpilot.wcs.infrastructure.movu.dto.MovuNotificationEnvelope;
import com.palletpilot.wcs.infrastructure.movu.security.MovuWebhookAuthFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Receives webhook notifications from Movu aSTC (IRIS REST API).
 * Supports two formats:
 * 
 * 1. Real aSTC envelope: { notificationType, payload{...}, notificationId,
 * timeStamp, contextType }
 * 2. Legacy flat format: { missionId, status } (backward compatibility)
 * 
 * Both single and batch (array) notifications are accepted.
 */
@RestController
@RequestMapping("/api/movu")
@Tag(name = "Movu Callbacks", description = "Webhook endpoint for Movu aSTC notifications")
@RequiredArgsConstructor
@Slf4j
public class MovuCallbackController {

    private final MovuWebhookParser webhookParser;
    private final MovuEventIngestService eventIngestService;
    private final MovuSecurityProperties securityProps;

    /**
     * Receive a single webhook notification from Movu aSTC.
     * Always returns 200 OK to acknowledge receipt (even for duplicates).
     * This prevents aSTC from buffering and retransmitting.
     */
    @PostMapping("/callback")
    @Operation(summary = "Receive Mission Update", description = "Called by Movu aSTC when a mission/task status changes. "
            +
            "Accepts both envelope and legacy flat formats.")
    public ResponseEntity<Void> handleCallback(@RequestBody Map<String, Object> payload,
            HttpServletRequest request) {
        String cid = MDC.get("correlationId");
        UUID resolvedEquipmentId = (UUID) request.getAttribute(MovuWebhookAuthFilter.EQUIPMENT_ID_ATTR);

        log.info("[{}] Received Movu Callback (equipment={}): {}", cid, resolvedEquipmentId, payload);

        try {
            MovuNotificationEnvelope envelope = webhookParser.parse(payload);

            // Timestamp staleness check (after successful parse)
            if (isTimestampStale(envelope.getTimeStamp())) {
                log.warn("[{}] Rejecting stale webhook: timestamp={}", cid, envelope.getTimeStamp());
                return ResponseEntity.badRequest().build();
            }

            // Pass resolved equipmentId for equipment resolution
            envelope.setResolvedEquipmentId(resolvedEquipmentId);

            boolean isNew = eventIngestService.ingest(envelope);

            if (isNew) {
                log.info("[{}] Processed Movu event: type={}, missionId={}, notificationId={}, equipment={}",
                        cid,
                        envelope.getNotificationType(),
                        envelope.getMissionPayload() != null ? envelope.getMissionPayload().getMissionId() : "N/A",
                        envelope.getNotificationId(),
                        resolvedEquipmentId);
            }

            return ResponseEntity.ok().build();

        } catch (IllegalArgumentException e) {
            log.warn("[{}] Invalid Movu callback payload: {}", cid, e.getMessage());
            return ResponseEntity.badRequest().build();

        } catch (Exception e) {
            log.error("[{}] Error processing Movu callback", cid, e);
            return ResponseEntity.ok().build();
        }
    }

    /**
     * Check if webhook timestamp is too old (replay protection).
     * Returns true if stale (should reject), false if OK.
     */
    private boolean isTimestampStale(String timestamp) {
        if (!securityProps.isEnabled() || timestamp == null || timestamp.isBlank())
            return false;
        try {
            Instant eventTime = Instant.parse(timestamp);
            long ageSec = Instant.now().getEpochSecond() - eventTime.getEpochSecond();
            return ageSec > securityProps.getTimestampMaxAgeSec();
        } catch (DateTimeParseException e) {
            log.debug("Cannot parse webhook timestamp: {}", timestamp);
            return false; // Don't reject on unparseable timestamps (legacy format may not have)
        }
    }

    /**
     * Receive batch webhook notifications from Movu aSTC.
     * aSTC may send buffered events in batches when the connection is
     * re-established.
     */
    @PostMapping("/callback/batch")
    @Operation(summary = "Receive Batch Mission Updates", description = "Called by Movu aSTC to deliver buffered notifications in batch.")
    public ResponseEntity<Void> handleBatchCallback(@RequestBody List<Map<String, Object>> payloads,
            HttpServletRequest request) {
        UUID resolvedEquipmentId = (UUID) request.getAttribute(MovuWebhookAuthFilter.EQUIPMENT_ID_ATTR);
        log.info("Received Movu Batch Callback: {} notifications (equipment={})",
                payloads.size(), resolvedEquipmentId);

        int processed = 0;
        int errors = 0;

        for (Map<String, Object> payload : payloads) {
            try {
                MovuNotificationEnvelope envelope = webhookParser.parse(payload);
                if (eventIngestService.ingest(envelope)) {
                    processed++;
                }
            } catch (Exception e) {
                errors++;
                log.warn("Error processing batch item: {}", e.getMessage());
            }
        }

        log.info("Batch processing complete: {} processed, {} errors out of {} total",
                processed, errors, payloads.size());

        return ResponseEntity.ok().build();
    }
}
