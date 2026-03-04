package com.palletpilot.wcs.application.service;

import com.palletpilot.wcs.application.service.MovuErrorNormalizer.MovuErrorCode;
import com.palletpilot.wcs.domain.model.MovuMission;
import com.palletpilot.wcs.infrastructure.movu.MovuClient;
import com.palletpilot.wcs.infrastructure.movu.exception.MovuExceptions.MovuUnavailableException;
import com.palletpilot.wcs.infrastructure.persistence.repository.MovuMissionRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Error remediation playbooks for Movu mission failures.
 *
 * Strategy per error code:
 * - LANE_FULL: Try updateDestination to alternate lane first (safe),
 * only escalate if that fails
 * - NOT_IN_ZONE: Manual intervention always (no automation)
 * - VEHICLE_ERROR: Alert + set DEGRADED if persistent
 * - COMMUNICATION_LOST: Handled by circuit breaker, just alert
 * - UNKNOWN: Manual intervention + alert
 *
 * Alert dedupe: max 1 alert per mission per error class per 5 minutes.
 */
@Service
@Slf4j
public class MovuRemediationService {

    private final MovuErrorNormalizer errorNormalizer;
    private final MovuClient movuClient;
    private final MovuMissionRepository missionRepo;
    private final AlertService alertService;

    /** Dedupe map: "missionId:errorCode" → last alert timestamp */
    private final ConcurrentHashMap<String, Instant> alertDedupe = new ConcurrentHashMap<>();
    private static final int ALERT_DEDUPE_SECONDS = 300; // 5 minutes

    public MovuRemediationService(MovuErrorNormalizer errorNormalizer,
            MovuClient movuClient,
            MovuMissionRepository missionRepo,
            AlertService alertService,
            MeterRegistry meterRegistry) {
        this.errorNormalizer = errorNormalizer;
        this.movuClient = movuClient;
        this.missionRepo = missionRepo;
        this.alertService = alertService;

        // Micrometer gauge: track dedupe map size for leak detection
        Gauge.builder("palletpilot.movu.remediation.alert_dedupe.size", alertDedupe, Map::size)
                .description("Number of active alert dedupe entries")
                .tag("component", "remediation")
                .register(meterRegistry);
    }

    /**
     * Handle a MissionError event. Normalize the error and apply remediation.
     */
    @Transactional
    public void handleMissionError(String missionId, String errorMessage,
            String missionStatus, UUID equipmentId) {
        String cid = MDC.get("correlationId");
        MovuErrorCode code = errorNormalizer.normalize(errorMessage, missionStatus);

        log.warn("[{}] Mission {} error classified as {}: {}",
                cid, missionId, code, errorMessage);

        switch (code) {
            case LANE_FULL -> handleLaneFull(missionId, equipmentId, cid);
            case NOT_IN_ZONE -> handleNotInZone(missionId, equipmentId, cid);
            case VEHICLE_ERROR -> handleVehicleError(missionId, equipmentId, cid, errorMessage);
            case COMMUNICATION_LOST -> handleCommunicationLost(missionId, equipmentId, cid);
            case UNKNOWN -> handleUnknown(missionId, equipmentId, cid, errorMessage);
        }
    }

    /**
     * LANE_FULL: Try updateDestination to alternate lane first.
     * This is safer than abort + recreate.
     */
    private void handleLaneFull(String missionId, UUID equipmentId, String cid) {
        log.info("[{}] Attempting LANE_FULL remediation for mission {}", cid, missionId);

        // Check mission state first
        Optional<MovuMission> missionOpt = missionRepo.findById(missionId);
        if (missionOpt.isEmpty()) {
            log.warn("[{}] Mission {} not found for remediation", cid, missionId);
            return;
        }

        MovuMission mission = missionOpt.get();
        String status = mission.getStatus();

        // Only attempt remediation if mission is in a state where destination can be
        // updated
        if ("PICKED".equals(status) || "ASSIGNED".equals(status) ||
                status != null && status.contains("AWAITING")) {
            try {
                // For MVP: escalate with clear instructions (full alternate lane logic
                // requires knowing available lanes, which is site-specific)
                raiseDeduped(missionId, MovuErrorCode.LANE_FULL, equipmentId,
                        "Mission " + missionId + " failed: LANE FULL. " +
                                "Operator: use dashboard to update destination to alternate lane, " +
                                "or abort and recreate if shuttle is blocked.",
                        "CRITICAL");

                // Update mission status
                mission.setStatus("REQUIRES_MANUAL_INTERVENTION");
                mission.setUpdatedAt(Instant.now());
                missionRepo.save(mission);

            } catch (MovuUnavailableException e) {
                log.warn("[{}] Cannot remediate LANE_FULL — circuit open", cid);
            }
        } else {
            raiseDeduped(missionId, MovuErrorCode.LANE_FULL, equipmentId,
                    "Mission " + missionId + " LANE FULL in unexpected state: " + status,
                    "WARNING");
        }
    }

    private void handleNotInZone(String missionId, UUID equipmentId, String cid) {
        log.warn("[{}] NOT_IN_ZONE for mission {} — manual intervention required", cid, missionId);

        updateMissionStatus(missionId, "REQUIRES_MANUAL_INTERVENTION");

        raiseDeduped(missionId, MovuErrorCode.NOT_IN_ZONE, equipmentId,
                "Mission " + missionId + ": shuttle NOT IN ZONE. " +
                        "Manual intervention required — verify shuttle position and zone configuration.",
                "CRITICAL");
    }

    private void handleVehicleError(String missionId, UUID equipmentId,
            String cid, String errorMessage) {
        log.warn("[{}] VEHICLE_ERROR for mission {}: {}", cid, missionId, errorMessage);

        updateMissionStatus(missionId, "FAILED");

        raiseDeduped(missionId, MovuErrorCode.VEHICLE_ERROR, equipmentId,
                "Mission " + missionId + " failed: VEHICLE ERROR — " + errorMessage,
                "CRITICAL");
    }

    private void handleCommunicationLost(String missionId, UUID equipmentId, String cid) {
        log.warn("[{}] COMMUNICATION_LOST for mission {} — circuit breaker handles this", cid, missionId);

        // Circuit breaker already handles this; just alert
        raiseDeduped(missionId, MovuErrorCode.COMMUNICATION_LOST, equipmentId,
                "Mission " + missionId + ": communication lost with aSTC. " +
                        "Circuit breaker will handle recovery automatically.",
                "WARNING");
    }

    private void handleUnknown(String missionId, UUID equipmentId,
            String cid, String errorMessage) {
        log.warn("[{}] UNKNOWN error for mission {}: {}", cid, missionId, errorMessage);

        updateMissionStatus(missionId, "REQUIRES_MANUAL_INTERVENTION");

        raiseDeduped(missionId, MovuErrorCode.UNKNOWN, equipmentId,
                "Mission " + missionId + " failed with unknown error: " +
                        (errorMessage != null ? errorMessage : "no details") +
                        ". Manual investigation required.",
                "CRITICAL");
    }

    /**
     * Deduplicated alert: max 1 per mission/errorCode per 5 minutes.
     */
    private void raiseDeduped(String missionId, MovuErrorCode code,
            UUID equipmentId, String message, String severity) {
        String key = missionId + ":" + code;
        Instant lastAlert = alertDedupe.get(key);

        if (lastAlert != null &&
                Instant.now().getEpochSecond() - lastAlert.getEpochSecond() < ALERT_DEDUPE_SECONDS) {
            log.debug("Suppressing duplicate alert for {} (code={})", missionId, code);
            return;
        }

        alertDedupe.put(key, Instant.now());
        alertService.raiseAlert(equipmentId, message, severity);
    }

    /**
     * Periodic cleanup: evicts dedupe entries older than 10 minutes.
     * Prevents unbounded memory growth from completed mission error keys.
     */
    @Scheduled(fixedRate = 600_000) // Every 10 minutes
    public void evictStaleAlerts() {
        if (alertDedupe.isEmpty())
            return;

        Instant threshold = Instant.now().minusSeconds(600);
        int before = alertDedupe.size();

        alertDedupe.entrySet().removeIf(entry -> entry.getValue().isBefore(threshold));

        int evicted = before - alertDedupe.size();
        if (evicted > 0) {
            log.info("[REMEDIATION] Evicted {} stale alert dedupe entries, {} remaining",
                    evicted, alertDedupe.size());
        }
    }

    private void updateMissionStatus(String missionId, String newStatus) {
        missionRepo.findById(missionId).ifPresent(mission -> {
            mission.setStatus(newStatus);
            mission.setUpdatedAt(Instant.now());
            missionRepo.save(mission);
        });
    }
}
