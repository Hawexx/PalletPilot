package com.palletpilot.wcs.application.service;

import com.palletpilot.wcs.domain.model.MovuLaneReservation;
import com.palletpilot.wcs.domain.model.MovuLoadPosition;
import com.palletpilot.wcs.domain.model.MovuPendingConfirmation;
import com.palletpilot.wcs.domain.model.MovuPendingConfirmation.ConfirmationStatus;
import com.palletpilot.wcs.domain.model.MovuPendingConfirmation.ConfirmationType;
import com.palletpilot.wcs.infrastructure.movu.MovuClient;
import com.palletpilot.wcs.infrastructure.movu.config.MovuLaneGeometryProperties;
import com.palletpilot.wcs.infrastructure.movu.exception.MovuExceptions.MovuUnavailableException;
import com.palletpilot.wcs.infrastructure.persistence.repository.MovuLaneReservationRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.MovuLoadPositionRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.MovuPendingConfirmationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles the "Late Decision" flow for Movu Atlas 2D.
 *
 * When aSTC sends MissionAwaitingDestination/SourceEntryConfirmation,
 * this service:
 * 1. Computes a safe distance based on current lane occupancy + reservations
 * 2. Reserves the chosen slot (unique constraint prevents collisions)
 * 3. Sends PUT destination/source to aSTC
 * 4. Sends POST confirmEnterDestination/Source to aSTC
 *
 * SAFETY: Slot reservation prevents two shuttles from targeting the same
 * physical position. This is a NON-NEGOTIABLE safety requirement.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MovuLateDecisionService {

    private final MovuClient movuClient;
    private final MovuLaneGeometryProperties laneProps;
    private final MovuPendingConfirmationRepository pendingRepo;
    private final MovuLaneReservationRepository reservationRepo;
    private final MovuLoadPositionRepository loadPositionRepo;
    private final AlertService alertService;

    private static final int RESERVATION_TTL_MINUTES = 10;
    private static final int MAX_ATTEMPTS = 3;

    /**
     * Process a pending confirmation: compute distance → reserve → update →
     * confirm.
     * Called by webhook handler (initial) and by scheduled retry job.
     */
    @Transactional
    public void process(MovuPendingConfirmation pending) {
        String cid = MDC.get("correlationId");
        String missionId = pending.getMissionId();
        UUID equipmentId = pending.getEquipmentId();

        log.info("[{}] Processing {} confirmation for mission {} (attempt #{})",
                cid, pending.getConfirmationType(), missionId, pending.getAttemptCount() + 1);

        // Mark as in-progress
        pending.setStatus(ConfirmationStatus.IN_PROGRESS);
        pending.setLastAttemptAt(Instant.now());
        pending.setAttemptCount(pending.getAttemptCount() + 1);
        pendingRepo.save(pending);

        try {
            // 1. Safety check: verify mission is still awaiting confirmation
            String currentStatus = movuClient.getMissionStatus(equipmentId, missionId);
            if (!isStillAwaiting(currentStatus, pending.getConfirmationType())) {
                log.info("[{}] Mission {} is no longer awaiting {} (status={}). Skipping.",
                        cid, missionId, pending.getConfirmationType(), currentStatus);
                pending.setStatus(ConfirmationStatus.SUCCEEDED);
                pendingRepo.save(pending);
                return;
            }

            // 2. Compute or reuse distance
            int distance;
            if (pending.getReservedDistanceMm() != null) {
                // Reuse previously reserved distance on retry
                distance = pending.getReservedDistanceMm();
                log.info("[{}] Reusing previously reserved distance: {}mm", cid, distance);
            } else {
                // First attempt: compute new distance
                OptionalInt computed = computeNextSlotDistance(
                        equipmentId, pending.getLevel(), pending.getLane(), pending.getSection());

                if (computed.isEmpty()) {
                    log.error("[{}] No available slot in lane L{}/LN{}/S{} for mission {}",
                            cid, pending.getLevel(), pending.getLane(), pending.getSection(), missionId);
                    pending.setStatus(ConfirmationStatus.FAILED);
                    pending.setErrorMessage("No available slot in lane");
                    pendingRepo.save(pending);

                    // Abort mission if safe
                    handleNoSlotAvailable(equipmentId, missionId, pending);
                    return;
                }

                distance = computed.getAsInt();

                // 3. Reserve the slot (unique constraint prevents collisions)
                try {
                    reserveSlot(equipmentId, pending.getLevel(), pending.getLane(),
                            pending.getSection(), distance, missionId, pending.getConfirmationType());
                    pending.setReservedDistanceMm(distance);
                    pendingRepo.save(pending);
                } catch (Exception e) {
                    log.warn("[{}] Slot {}mm already reserved in lane. Trying next slot.", cid, distance);
                    // Retry with next available slot
                    pending.setStatus(ConfirmationStatus.PENDING);
                    pending.setNextRetryAt(Instant.now().plusSeconds(2));
                    pendingRepo.save(pending);
                    return;
                }
            }

            // 4. Send update to aSTC
            Map<String, Object> locationUpdate = new HashMap<>();
            locationUpdate.put("level", pending.getLevel());
            locationUpdate.put("lane", pending.getLane());
            locationUpdate.put("section", pending.getSection());
            locationUpdate.put("distance", distance);

            if (pending.getConfirmationType() == ConfirmationType.DESTINATION) {
                locationUpdate.put("approach", "Back"); // Default approach
                movuClient.updateDestination(equipmentId, missionId, locationUpdate);
                log.info("[{}] Updated destination for mission {} to {}mm", cid, missionId, distance);
            } else {
                locationUpdate.put("exit", "Front"); // Default exit
                movuClient.updateSource(equipmentId, missionId, locationUpdate);
                log.info("[{}] Updated source for mission {} to {}mm", cid, missionId, distance);
            }

            // 5. Send confirmation to aSTC
            if (pending.getConfirmationType() == ConfirmationType.DESTINATION) {
                movuClient.confirmEnterDestination(equipmentId, missionId);
            } else {
                movuClient.confirmEnterSource(equipmentId, missionId);
            }

            // 6. Mark as succeeded
            pending.setStatus(ConfirmationStatus.SUCCEEDED);
            pendingRepo.save(pending);
            log.info("[{}] Successfully confirmed {} for mission {} at distance {}mm",
                    cid, pending.getConfirmationType(), missionId, distance);

        } catch (MovuUnavailableException e) {
            // Circuit breaker open — retry later
            log.warn("[{}] Circuit breaker open for mission {} — deferring retry", cid, missionId);
            pending.setStatus(ConfirmationStatus.PENDING);
            pending.setNextRetryAt(Instant.now().plusSeconds(30));
            pending.setErrorMessage("Circuit breaker open: " + e.getMessage());
            pendingRepo.save(pending);

        } catch (Exception e) {
            log.error("[{}] Error processing confirmation for mission {}: {}",
                    cid, missionId, e.getMessage(), e);

            if (pending.getAttemptCount() >= MAX_ATTEMPTS) {
                pending.setStatus(ConfirmationStatus.FAILED);
                pending.setErrorMessage("Max attempts reached: " + e.getMessage());
                alertService.raiseAlert(equipmentId,
                        "Late decision failed after " + MAX_ATTEMPTS + " attempts for mission " + missionId,
                        "CRITICAL");
            } else {
                pending.setStatus(ConfirmationStatus.PENDING);
                pending.setNextRetryAt(Instant.now().plusSeconds(5L * pending.getAttemptCount()));
                pending.setErrorMessage(e.getMessage());
            }
            pendingRepo.save(pending);
        }
    }

    /**
     * Compute the next available slot distance in a lane.
     * Considers both existing load positions AND active reservations.
     */
    private OptionalInt computeNextSlotDistance(UUID equipmentId, int level, int lane, int section) {
        // Gather occupied distances: load positions + active reservations
        Set<Integer> occupiedDistances = new TreeSet<>();

        // 1. Existing loads in lane
        List<MovuLoadPosition> loads = loadPositionRepo.findByLevelAndLaneAndSection(level, lane, section);
        loads.forEach(lp -> occupiedDistances.add(lp.getDistanceMm()));

        // 2. Active reservations in lane (not expired)
        List<MovuLaneReservation> reservations = reservationRepo
                .findByEquipmentIdAndLevelAndLaneAndSectionAndExpiresAtAfter(
                        equipmentId, level, lane, section, Instant.now());
        reservations.forEach(r -> occupiedDistances.add(r.getDistanceMm()));

        log.debug("Lane L{}/LN{}/S{} occupied distances: {}", level, lane, section, occupiedDistances);

        // 3. Compute candidate slots from front to back
        int maxLoadLen = laneProps.getMaxLoadLengthMm();
        int gap = laneProps.getDefaultGapMm();
        int offsetFront = laneProps.getOffsetFrontMm();
        int offsetBack = laneProps.getOffsetBackMm();
        int laneLength = laneProps.getLaneLengthMm();

        for (int n = 1; n <= 50; n++) { // Safety limit
            int slotCenter = offsetFront + (n - 1) * (maxLoadLen + gap) + maxLoadLen / 2;

            // Check if slot exceeds lane length
            if (slotCenter + maxLoadLen / 2 > laneLength - offsetBack) {
                break; // No more slots fit
            }

            // Check if slot is far enough from all occupied positions
            boolean isFree = occupiedDistances.stream()
                    .noneMatch(occupied -> Math.abs(slotCenter - occupied) < (maxLoadLen + gap));

            if (isFree) {
                return OptionalInt.of(slotCenter);
            }
        }

        return OptionalInt.empty(); // Lane is full
    }

    /**
     * Reserve a slot with unique constraint enforcement.
     */
    private void reserveSlot(UUID equipmentId, int level, int lane, int section,
            int distanceMm, String missionId,
            ConfirmationType confirmationType) {
        MovuLaneReservation reservation = MovuLaneReservation.builder()
                .equipmentId(equipmentId)
                .level(level)
                .lane(lane)
                .section(section)
                .distanceMm(distanceMm)
                .missionId(missionId)
                .confirmationType(confirmationType)
                .expiresAt(Instant.now().plusSeconds(RESERVATION_TTL_MINUTES * 60L))
                .build();

        reservationRepo.save(reservation);
        log.info("Reserved slot: L{}/LN{}/S{} at {}mm for mission {}",
                level, lane, section, distanceMm, missionId);
    }

    /**
     * Handle case where no slot is available in the lane.
     * Abort the mission and raise CRITICAL alert.
     */
    private void handleNoSlotAvailable(UUID equipmentId, String missionId,
            MovuPendingConfirmation pending) {
        try {
            movuClient.abortMission(equipmentId, missionId);
            log.warn("Aborted mission {} — no available slot", missionId);
        } catch (Exception e) {
            log.error("Failed to abort mission {}: {}", missionId, e.getMessage());
        }

        alertService.raiseAlert(equipmentId,
                "Mission " + missionId + " aborted: lane L" + pending.getLevel() +
                        "/LN" + pending.getLane() + "/S" + pending.getSection() + " is full",
                "CRITICAL");
    }

    /**
     * Clean up reservations for a completed/aborted/cancelled mission.
     */
    @Transactional
    public void cleanupReservations(String missionId) {
        reservationRepo.deleteByMissionId(missionId);
        log.debug("Cleaned up reservations for mission {}", missionId);
    }

    /**
     * Clean up expired reservations (safety net, called periodically).
     */
    @Transactional
    public void cleanupExpiredReservations() {
        reservationRepo.deleteByExpiresAtBefore(Instant.now());
    }

    private boolean isStillAwaiting(String status, ConfirmationType type) {
        if (status == null)
            return false;
        String upper = status.toUpperCase();
        if (type == ConfirmationType.DESTINATION) {
            return upper.contains("AWAITING") && upper.contains("DESTINATION");
        } else {
            return upper.contains("AWAITING") && upper.contains("SOURCE");
        }
    }
}
