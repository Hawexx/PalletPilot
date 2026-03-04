package com.palletpilot.wcs.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.palletpilot.wcs.domain.model.MovuLoadPosition;
import com.palletpilot.wcs.domain.model.MovuMission;
import com.palletpilot.wcs.domain.model.MovuMissionEvent;
import com.palletpilot.wcs.domain.model.MovuPendingConfirmation;
import com.palletpilot.wcs.domain.model.MovuPendingConfirmation.ConfirmationType;
import com.palletpilot.wcs.domain.model.enums.MovuMissionStatus;
import com.palletpilot.wcs.infrastructure.movu.dto.MovuLocation;
import com.palletpilot.wcs.infrastructure.movu.dto.MovuMissionPayload;
import com.palletpilot.wcs.infrastructure.movu.dto.MovuNotificationEnvelope;
import com.palletpilot.wcs.infrastructure.persistence.repository.MovuLoadPositionRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.MovuMissionEventRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.MovuMissionRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.MovuPendingConfirmationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Idempotent event ingestion service for Movu webhook notifications.
 * 
 * Responsibilities:
 * 1. Deduplication via notification_id UNIQUE constraint
 * 2. Append event to audit log (movu_mission_events)
 * 3. State machine validation (prevent regression)
 * 4. Update MovuMission aggregate status
 * 5. Persist load positions from MissionDropped feedback
 * 6. Trigger downstream actions (handoff, alerts)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MovuEventIngestService {

    private final MovuMissionEventRepository eventRepository;
    private final MovuMissionRepository missionRepository;
    private final MovuLoadPositionRepository loadPositionRepository;
    private final MovuPendingConfirmationRepository pendingConfirmationRepository;
    private final MovuMissionStateMachine stateMachine;
    private final MovuRemediationService remediationService;
    private final AlertService alertService;
    private final ObjectMapper objectMapper;
    private final WebSocketBroadcastService wsBroadcast;
    private final MovuService movuService;

    /**
     * Ingest a parsed Movu webhook notification.
     *
     * @param envelope The typed notification envelope
     * @return true if new event was processed, false if duplicate (no-op)
     */
    @Transactional
    public boolean ingest(MovuNotificationEnvelope envelope) {
        String notificationId = envelope.getNotificationId();

        // 1. Idempotency check
        if (eventRepository.existsByNotificationId(notificationId)) {
            log.debug("Duplicate notification ignored: {}", notificationId);
            return false;
        }

        MovuMissionPayload payload = envelope.getMissionPayload();
        String missionId = payload != null ? payload.getMissionId() : null;

        if (missionId == null || missionId.isBlank()) {
            log.warn("Webhook notification {} has no missionId, storing event only", notificationId);
            persistEvent(envelope, null);
            return true;
        }

        // 2. Persist event to audit log
        MovuMissionEvent event = persistEvent(envelope, missionId);

        // 3. Determine target status from notificationType
        MovuMissionStatus targetStatus = MovuMissionStatus.fromNotificationType(
                envelope.getNotificationType());

        // 4. Update mission aggregate (if mission exists in our DB)
        missionRepository.findById(missionId).ifPresentOrElse(
                mission -> processMissionUpdate(mission, envelope, targetStatus),
                () -> {
                    // Mission not in our DB — could be created externally or via aSTC HMI
                    log.info("Received event for unknown mission {}: {}. Creating tracking record.",
                            missionId, envelope.getNotificationType());
                    createTrackingMission(missionId, envelope, targetStatus);
                });

        return true;
    }

    /**
     * Update an existing mission's status based on the webhook event.
     */
    private void processMissionUpdate(MovuMission mission, MovuNotificationEnvelope envelope,
            MovuMissionStatus targetStatus) {
        String missionId = mission.getId();
        String notificationType = envelope.getNotificationType();

        // State machine validation
        if (targetStatus != null && stateMachine.canTransition(mission.getStatus(), targetStatus)) {
            String oldStatus = mission.getStatus();
            mission.setStatus(targetStatus.name());
            mission.setUpdatedAt(Instant.now());
            missionRepository.save(mission);
            log.info("[MOVU] Mission {} status: {} → {} (via {})",
                    missionId, oldStatus, targetStatus, notificationType);

            // Push real-time update to frontend
            wsBroadcast.sendMovuEvent("MISSION_UPDATE", java.util.Map.of(
                    "missionId", missionId,
                    "oldStatus", oldStatus,
                    "newStatus", targetStatus.name(),
                    "notificationType", notificationType));
        } else if (targetStatus == null) {
            // Events like MissionSourceChanged/MissionDestinationChanged don't change
            // status
            log.debug("Mission {} received non-status event: {}", missionId, notificationType);
        }

        // 5. Handle specific notification types
        MovuMissionPayload payload = envelope.getMissionPayload();

        switch (notificationType) {
            case "MissionDropped" -> handleMissionDropped(mission, payload);
            case "MissionError" -> handleMissionError(mission, payload);
            case "MissionAborted" -> handleMissionAborted(mission);
            case "MissionAwaitingDestinationEntryConfirmation" ->
                createPendingConfirmation(mission, payload, ConfirmationType.DESTINATION);
            case "MissionAwaitingSourceEntryConfirmation" ->
                createPendingConfirmation(mission, payload, ConfirmationType.SOURCE);
            default -> {
                /* No special handling needed */ }
        }
    }

    /**
     * MissionDropped: mission completed successfully.
     * Save actual drop distance for future retrievals + trigger handoff.
     */
    private void handleMissionDropped(MovuMission mission, MovuMissionPayload payload) {
        log.info("Mission {} completed (dropped). Triggering downstream actions.", mission.getId());

        // Persist actual drop distance (Phase B: Load Position Memory)
        if (payload != null && payload.getDestination() != null && payload.getLoadId() != null) {
            persistLoadPosition(payload.getLoadId(), payload.getDestination(), mission.getId());
        }

        // Trigger conveyor handoff (only for PalletPilot-initiated missions)
        if (mission.getTargetLocation() != null
                && !"EXTERNAL".equals(mission.getTargetLocation())
                && mission.getEquipmentId() != null) {
            triggerHandoff(mission);
        }
    }

    /**
     * MissionError: extract error codes and raise appropriate alerts.
     */
    private void handleMissionError(MovuMission mission, MovuMissionPayload payload) {
        String errorDetail = "Unknown error";
        if (payload != null && payload.getErrors() != null && !payload.getErrors().isEmpty()) {
            errorDetail = payload.getErrors().stream()
                    .map(e -> e.getMessage())
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("Unknown error");
        }

        log.error("Mission {} FAILED: {}", mission.getId(), errorDetail);

        // Delegate to remediation service for automated error handling (Phase F)
        try {
            remediationService.handleMissionError(
                    mission.getId(), errorDetail, mission.getStatus(), mission.getEquipmentId());
        } catch (Exception e) {
            log.warn("Remediation failed for mission {}: {}", mission.getId(), e.getMessage());
            // Fallback: raise basic alert
            if (mission.getEquipmentId() != null) {
                alertService.raiseAlert(
                        mission.getEquipmentId(),
                        "Movu Mission " + mission.getId() + " error: " + errorDetail,
                        "ERROR");
            }
        }
    }

    /**
     * MissionAborted: forceful termination, may require manual intervention.
     */
    private void handleMissionAborted(MovuMission mission) {
        log.warn("Mission {} ABORTED. Manual intervention may be required.", mission.getId());
        if (mission.getEquipmentId() != null) {
            alertService.raiseAlert(
                    mission.getEquipmentId(),
                    "Movu Mission " + mission.getId() + " was aborted. Check for shuttle with load.",
                    "WARNING");
        }
    }

    /**
     * Create a pending confirmation record for AWAITING_SOURCE/DEST_CONFIRM events.
     * The MovuPendingConfirmationScheduler picks these up and delegates to
     * MovuLateDecisionService for distance calculation + slot reservation +
     * confirm.
     */
    private void createPendingConfirmation(MovuMission mission, MovuMissionPayload payload,
            ConfirmationType type) {
        log.info("Mission {} awaiting {} confirmation. Creating pending record.",
                mission.getId(), type);

        // Extract lane/level/section from destination or source
        MovuLocation loc = (type == ConfirmationType.DESTINATION && payload != null)
                ? payload.getDestination()
                : (payload != null ? payload.getSource() : null);

        UUID equipmentId = mission.getEquipmentId();
        if (equipmentId == null) {
            log.warn("Mission {} has no equipmentId, cannot create pending confirmation", mission.getId());
            return;
        }

        MovuPendingConfirmation pending = MovuPendingConfirmation.builder()
                .equipmentId(equipmentId)
                .missionId(mission.getId())
                .confirmationType(type)
                .lane(loc != null ? loc.getLane() : null)
                .level(loc != null ? loc.getLevel() : null)
                .section(loc != null ? loc.getSection() : null)
                .build();

        try {
            pendingConfirmationRepository.save(pending);
            log.info("Created pending {} confirmation for mission {} (lane={}, level={})",
                    type, mission.getId(),
                    loc != null ? loc.getLane() : "?",
                    loc != null ? loc.getLevel() : "?");
        } catch (DataIntegrityViolationException e) {
            // Idempotent: unique constraint on (mission_id, confirmation_type)
            log.debug("Pending confirmation already exists for mission {} type {}", mission.getId(), type);
        }
    }

    /**
     * Persist actual drop distance into load position memory.
     * This is critical for Atlas 2D: the next retrieve must use this exact
     * distance.
     */
    private void persistLoadPosition(String loadId, MovuLocation destination, String missionId) {
        if (destination.getDistance() == null) {
            log.debug("MissionDropped destination has no distance (eScala uses index). Skipping position save.");
            return;
        }

        MovuLoadPosition position = loadPositionRepository.findById(loadId)
                .orElse(new MovuLoadPosition());

        position.setLoadId(loadId);
        position.setLevel(destination.getLevel() != null ? destination.getLevel() : 0);
        position.setLane(destination.getLane() != null ? destination.getLane() : 0);
        position.setSection(destination.getSection() != null ? destination.getSection() : 0);
        position.setDistanceMm(destination.getDistance());
        position.setLastMissionId(missionId);
        position.setLastUpdated(Instant.now());

        loadPositionRepository.save(position);
        log.info("Load {} position saved: Level={}, Lane={}, Section={}, Distance={}mm",
                loadId, position.getLevel(), position.getLane(), position.getSection(), position.getDistanceMm());
    }

    /**
     * Trigger conveyor/PLC handoff after successful mission completion.
     * Delegates to MovuService which controls the physical conveyor via PlcService.
     */
    private void triggerHandoff(MovuMission mission) {
        try {
            movuService.triggerHandoff(mission.getTargetLocation());
            log.info("[HANDOFF] PLC handoff triggered for mission {} → '{}'",
                    mission.getId(), mission.getTargetLocation());
        } catch (Exception e) {
            log.error("[HANDOFF] PLC handoff FAILED for mission {} → '{}': {}",
                    mission.getId(), mission.getTargetLocation(), e.getMessage());
            alertService.raiseAlert(null,
                    String.format("PLC handoff failed for mission %s: %s",
                            mission.getId(), e.getMessage()),
                    "CRITICAL");
        }
    }

    /**
     * Create a tracking record for missions not yet in our DB.
     * This handles cases where missions are created via aSTC HMI or external
     * systems.
     */
    private void createTrackingMission(String missionId, MovuNotificationEnvelope envelope,
            MovuMissionStatus targetStatus) {
        MovuMissionPayload payload = envelope.getMissionPayload();

        MovuMission mission = MovuMission.builder()
                .id(missionId)
                .sku("UNKNOWN") // External missions have no SKU context
                .sourceLocation(payload != null && payload.getSource() != null
                        ? formatLocation(payload.getSource())
                        : "EXTERNAL")
                .targetLocation(payload != null && payload.getDestination() != null
                        ? formatLocation(payload.getDestination())
                        : "EXTERNAL")
                .status(targetStatus != null ? targetStatus.name() : "UNKNOWN")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        missionRepository.save(mission);
        log.info("Created tracking for external mission {}: {}", missionId, mission.getStatus());
    }

    /**
     * Persist a webhook event to the append-only audit log.
     */
    private MovuMissionEvent persistEvent(MovuNotificationEnvelope envelope, String missionId) {
        String payloadJson = null;
        try {
            payloadJson = objectMapper.writeValueAsString(envelope.getMissionPayload());
        } catch (JsonProcessingException e) {
            log.warn("Could not serialize webhook payload to JSON", e);
        }

        Instant eventTimestamp = null;
        if (envelope.getTimeStamp() != null) {
            try {
                eventTimestamp = Instant.parse(envelope.getTimeStamp());
            } catch (Exception e) {
                log.debug("Could not parse timestamp '{}', using now", envelope.getTimeStamp());
                eventTimestamp = Instant.now();
            }
        }

        MovuMissionPayload payload = envelope.getMissionPayload();

        MovuMissionEvent event = MovuMissionEvent.builder()
                .notificationId(envelope.getNotificationId())
                .missionId(missionId != null ? missionId : "UNKNOWN")
                .notificationType(envelope.getNotificationType())
                .vehicleId(payload != null ? payload.getVehicleId() : null)
                .loadId(payload != null ? payload.getLoadId() : null)
                .payloadJson(payloadJson)
                .eventTimestamp(eventTimestamp)
                .processedAt(Instant.now())
                .build();

        return eventRepository.save(event);
    }

    /**
     * Format a MovuLocation into a readable string for DB storage.
     */
    private String formatLocation(MovuLocation loc) {
        return String.format("L%d-Ln%d-S%d-D%d",
                loc.getLevel() != null ? loc.getLevel() : 0,
                loc.getLane() != null ? loc.getLane() : 0,
                loc.getSection() != null ? loc.getSection() : 0,
                loc.getDistance() != null ? loc.getDistance() : 0);
    }
}
