package com.palletpilot.wcs.application.service;

import com.palletpilot.wcs.domain.model.MovuMission;
import com.palletpilot.wcs.infrastructure.movu.MovuClient;
import com.palletpilot.wcs.infrastructure.movu.exception.MovuExceptions.MovuUnavailableException;
import com.palletpilot.wcs.infrastructure.persistence.repository.MovuMissionRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Reconciliation watchdog for Movu missions.
 * Detects missions stuck in RUNNING/CREATED/ASSIGNED/PICKED states and
 * actively queries aSTC for the real status.
 * 
 * Phase C: Respects circuit breaker state — skips reconciliation when
 * aSTC is unreachable to avoid stacking failed queries.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StaleMissionWatchdog {

    private final MovuMissionRepository missionRepository;
    private final MovuClient movuClient;
    private final SystemStateService systemStateService;
    private final AlertService alertService;

    private static final List<String> ACTIVE_STATUSES = List.of(
            "RUNNING", "CREATED", "ASSIGNED", "PICKED");

    @Scheduled(fixedDelay = 60000) // Run every minute
    @Transactional
    public void checkStaleMissions() {
        // 1. Safety: Skip if E-Stopped
        if (systemStateService.isEmergencyStop()) {
            log.debug("Watchdog skipped due to E-STOP state.");
            return;
        }

        // 2. Phase C: Skip if circuit breaker is OPEN (aSTC unreachable)
        if (movuClient.getCircuitState() == CircuitBreaker.State.OPEN) {
            log.info("Watchdog skipped: Movu circuit breaker is OPEN. " +
                    "aSTC unreachable — cannot reconcile.");
            return;
        }

        // 3. Find stuck missions (active status > 10 minutes without update)
        Instant threshold = Instant.now().minus(10, ChronoUnit.MINUTES);
        List<MovuMission> stuckMissions = missionRepository.findByStatusInAndUpdatedAtBefore(
                ACTIVE_STATUSES, threshold);

        if (stuckMissions.isEmpty())
            return;

        log.info("Watchdog found {} potential stale missions", stuckMissions.size());

        for (MovuMission mission : stuckMissions) {
            handleStaleMission(mission);
        }
    }

    private void handleStaleMission(MovuMission mission) {
        try {
            // Active reconciliation — query aSTC for real status
            String realStatus = movuClient.getMissionStatus(
                    mission.getEquipmentId(), mission.getId());

            if ("COMPLETED".equalsIgnoreCase(realStatus)) {
                log.info("Reconciled Mission {}: COMPLETED", mission.getId());
                mission.setStatus("COMPLETED");
                mission.setUpdatedAt(Instant.now());
                missionRepository.save(mission);
            } else if ("FAILED".equalsIgnoreCase(realStatus)) {
                log.warn("Reconciled Mission {}: FAILED", mission.getId());
                mission.setStatus("FAILED");
                mission.setUpdatedAt(Instant.now());
                missionRepository.save(mission);
                if (mission.getEquipmentId() != null) {
                    alertService.raiseAlert(mission.getEquipmentId(),
                            "Mission Reconciled as FAILED: " + mission.getId(), "ERROR");
                }
            } else if ("UNKNOWN".equalsIgnoreCase(realStatus)) {
                // Equipment unreachable or external mission
                log.warn("Could not reach equipment for Mission {}. Status UNKNOWN.", mission.getId());
                if (mission.getUpdatedAt().isBefore(Instant.now().minus(30, ChronoUnit.MINUTES))) {
                    escalateToManual(mission, "Equipment unreachable > 30 mins");
                }
            } else {
                // Still active on device but stale in our DB
                if (mission.getUpdatedAt().isBefore(Instant.now().minus(60, ChronoUnit.MINUTES))) {
                    escalateToManual(mission, "Mission timed out > 60 mins");
                }
            }

        } catch (MovuUnavailableException e) {
            // Phase C: Circuit breaker just opened during this iteration
            // Don't mark missions as FAILED — this is a connectivity issue, not a mission
            // failure
            log.warn("Circuit breaker opened during reconciliation of mission {} — " +
                    "skipping remaining missions.", mission.getId());
            // Stop processing remaining missions to avoid stacking failures
        } catch (Exception e) {
            log.error("Error reconciling mission {}: {}", mission.getId(), e.getMessage());
        }
    }

    private void escalateToManual(MovuMission mission, String reason) {
        log.error("Escalating Mission {} to MANUAL INTERVENTION. Reason: {}",
                mission.getId(), reason);
        mission.setStatus("REQUIRES_MANUAL_INTERVENTION");
        mission.setUpdatedAt(Instant.now());
        missionRepository.save(mission);
        if (mission.getEquipmentId() != null) {
            alertService.raiseAlert(mission.getEquipmentId(),
                    "Mission " + mission.getId() + " requires manual intervention: " + reason,
                    "CRITICAL");
        }
    }
}
