package com.palletpilot.wcs.application.service;

import com.palletpilot.wcs.domain.model.enums.MovuMissionStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Validates and manages mission state transitions.
 * Prevents invalid state regressions (e.g., COMPLETED → PICKED)
 * and logs warnings for out-of-order events.
 */
@Component
@Slf4j
public class MovuMissionStateMachine {

    /**
     * Allowed state transitions. The key is the current state,
     * the value is the set of valid next states.
     */
    private static final Map<MovuMissionStatus, Set<MovuMissionStatus>> ALLOWED_TRANSITIONS = Map.ofEntries(
            Map.entry(MovuMissionStatus.CREATED, Set.of(
                    MovuMissionStatus.ASSIGNING,
                    MovuMissionStatus.ASSIGNED,
                    MovuMissionStatus.CANCELLED,
                    MovuMissionStatus.ABORTED,
                    MovuMissionStatus.FAILED)),
            Map.entry(MovuMissionStatus.ASSIGNING, Set.of(
                    MovuMissionStatus.ASSIGNED,
                    MovuMissionStatus.CANCELLED,
                    MovuMissionStatus.ABORTED,
                    MovuMissionStatus.FAILED)),
            Map.entry(MovuMissionStatus.ASSIGNED, Set.of(
                    MovuMissionStatus.AWAITING_SOURCE_CONFIRM,
                    MovuMissionStatus.PICKED,
                    MovuMissionStatus.CANCELLED,
                    MovuMissionStatus.ABORTED,
                    MovuMissionStatus.FAILED)),
            Map.entry(MovuMissionStatus.AWAITING_SOURCE_CONFIRM, Set.of(
                    MovuMissionStatus.PICKED,
                    MovuMissionStatus.CANCELLED,
                    MovuMissionStatus.ABORTED,
                    MovuMissionStatus.FAILED)),
            Map.entry(MovuMissionStatus.PICKED, Set.of(
                    MovuMissionStatus.AWAITING_DEST_CONFIRM,
                    MovuMissionStatus.COMPLETED,
                    MovuMissionStatus.ABORTED,
                    MovuMissionStatus.FAILED)),
            Map.entry(MovuMissionStatus.AWAITING_DEST_CONFIRM, Set.of(
                    MovuMissionStatus.COMPLETED,
                    MovuMissionStatus.ABORTED,
                    MovuMissionStatus.FAILED)),
            // Terminal states: allow REQUIRES_MANUAL_INTERVENTION as escalation
            Map.entry(MovuMissionStatus.COMPLETED, Set.of()),
            Map.entry(MovuMissionStatus.CANCELLED, Set.of()),
            Map.entry(MovuMissionStatus.ABORTED, Set.of(
                    MovuMissionStatus.REQUIRES_MANUAL_INTERVENTION)),
            Map.entry(MovuMissionStatus.FAILED, Set.of(
                    MovuMissionStatus.REQUIRES_MANUAL_INTERVENTION)),
            Map.entry(MovuMissionStatus.REQUIRES_MANUAL_INTERVENTION, Set.of()),
            Map.entry(MovuMissionStatus.UNKNOWN, Set.of()) // wildcard: anything goes from UNKNOWN
    );

    /**
     * Attempt to transition a mission to a new status.
     *
     * @param currentStatusStr Current status string from the mission entity
     * @param newStatus        The new status derived from the webhook notification
     * @return true if transition is valid and should be applied; false if rejected
     */
    public boolean canTransition(String currentStatusStr, MovuMissionStatus newStatus) {
        if (newStatus == null) {
            // Some notification types (e.g. MissionSourceChanged) don't map to a status
            return false;
        }

        MovuMissionStatus currentStatus = parseCurrentStatus(currentStatusStr);

        // UNKNOWN current state → allow any transition (best-effort recovery)
        if (currentStatus == MovuMissionStatus.UNKNOWN) {
            log.info("Mission in UNKNOWN state, allowing transition to {}", newStatus);
            return true;
        }

        // Forward progression or same state
        if (currentStatus == newStatus) {
            log.debug("Mission already in state {}, no-op", newStatus);
            return false; // Idempotent: same state → no change needed
        }

        Set<MovuMissionStatus> allowedNext = ALLOWED_TRANSITIONS.getOrDefault(currentStatus, Set.of());

        if (allowedNext.contains(newStatus)) {
            log.debug("Valid transition: {} → {}", currentStatus, newStatus);
            return true;
        }

        // Forward progression by order — allow skipping states (e.g. CREATED → PICKED)
        // This handles cases where intermediate webhook notifications were lost
        if (newStatus.getOrder() > currentStatus.getOrder()) {
            log.warn("Non-standard but forward transition: {} → {} (skipped intermediate states)",
                    currentStatus, newStatus);
            return true;
        }

        // Reject regression
        log.warn("REJECTED state regression: {} → {}", currentStatus, newStatus);
        return false;
    }

    /**
     * Parse the current status string from the DB entity to the enum.
     * Handles both old-format strings ("RUNNING") and new enum names.
     */
    private MovuMissionStatus parseCurrentStatus(String statusStr) {
        if (statusStr == null || statusStr.isBlank()) {
            return MovuMissionStatus.UNKNOWN;
        }

        // Map legacy status strings
        return switch (statusStr.toUpperCase()) {
            case "RUNNING" -> MovuMissionStatus.ASSIGNED; // Old "RUNNING" maps to ASSIGNED
            case "CREATED" -> MovuMissionStatus.CREATED;
            case "ASSIGNING" -> MovuMissionStatus.ASSIGNING;
            case "ASSIGNED" -> MovuMissionStatus.ASSIGNED;
            case "PICKED" -> MovuMissionStatus.PICKED;
            case "COMPLETED" -> MovuMissionStatus.COMPLETED;
            case "CANCELLED" -> MovuMissionStatus.CANCELLED;
            case "ABORTED" -> MovuMissionStatus.ABORTED;
            case "FAILED" -> MovuMissionStatus.FAILED;
            case "AWAITING_SOURCE_CONFIRM" -> MovuMissionStatus.AWAITING_SOURCE_CONFIRM;
            case "AWAITING_DEST_CONFIRM" -> MovuMissionStatus.AWAITING_DEST_CONFIRM;
            case "REQUIRES_MANUAL_INTERVENTION" -> MovuMissionStatus.REQUIRES_MANUAL_INTERVENTION;
            default -> {
                log.warn("Unknown mission status in DB: '{}'", statusStr);
                yield MovuMissionStatus.UNKNOWN;
            }
        };
    }
}
