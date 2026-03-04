package com.palletpilot.wcs.infrastructure.movu;

import com.palletpilot.wcs.domain.model.MovuMissionEvent;
import com.palletpilot.wcs.domain.model.MovuPendingConfirmation;
import com.palletpilot.wcs.domain.model.MovuPendingConfirmation.ConfirmationStatus;
import com.palletpilot.wcs.application.service.SystemStateService;
import com.palletpilot.wcs.infrastructure.persistence.repository.MovuMissionEventRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.MovuMissionRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.MovuPendingConfirmationRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Operational REST endpoints for Movu mission monitoring.
 * Provides timeline, active missions, pending confirmations, and health.
 */
@RestController
@RequestMapping("/api/movu")
@Tag(name = "Movu Operations", description = "Operational monitoring endpoints")
@RequiredArgsConstructor
@Slf4j
public class MovuOpsController {

    private final MovuMissionEventRepository eventRepository;
    private final MovuMissionRepository missionRepository;
    private final MovuPendingConfirmationRepository pendingConfirmationRepo;
    private final MovuClient movuClient;
    private final SystemStateService systemStateService;

    /**
     * H1: Get the full event timeline for a specific mission.
     */
    @GetMapping("/missions/{missionId}/timeline")
    @Operation(summary = "Mission Timeline", description = "Returns ordered event log for a specific mission")
    public ResponseEntity<List<Map<String, Object>>> getMissionTimeline(@PathVariable String missionId) {
        List<MovuMissionEvent> events = eventRepository.findByMissionIdOrderByEventTimestampAsc(missionId);
        if (events.isEmpty())
            return ResponseEntity.notFound().build();

        List<Map<String, Object>> timeline = events.stream()
                .map(e -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("notificationType", e.getNotificationType());
                    entry.put("vehicleId", e.getVehicleId());
                    entry.put("loadId", e.getLoadId());
                    entry.put("eventTimestamp", e.getEventTimestamp());
                    entry.put("processedAt", e.getProcessedAt());
                    return entry;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(timeline);
    }

    /**
     * H2: Get all active (non-terminal) missions.
     */
    @GetMapping("/missions/active")
    @Operation(summary = "Active Missions", description = "Returns all non-terminal missions with latest status")
    public ResponseEntity<List<Map<String, Object>>> getActiveMissions() {
        List<String> terminalStatuses = List.of("COMPLETED", "FAILED", "CANCELLED", "ABORTED",
                "REQUIRES_MANUAL_INTERVENTION");

        var allMissions = missionRepository.findAll();
        List<Map<String, Object>> active = allMissions.stream()
                .filter(m -> !terminalStatuses.contains(m.getStatus()))
                .map(m -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("missionId", m.getId());
                    entry.put("status", m.getStatus());
                    entry.put("equipmentId", m.getEquipmentId());
                    entry.put("sku", m.getSku());
                    entry.put("sourceLocation", m.getSourceLocation());
                    entry.put("targetLocation", m.getTargetLocation());
                    entry.put("createdAt", m.getCreatedAt());
                    entry.put("updatedAt", m.getUpdatedAt());
                    return entry;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(active);
    }

    /**
     * H3: Get pending confirmations that need operator attention.
     */
    @GetMapping("/confirmations/pending")
    @Operation(summary = "Pending Confirmations", description = "Confirmations awaiting processing or failed")
    public ResponseEntity<List<Map<String, Object>>> getPendingConfirmations() {
        List<MovuPendingConfirmation> pending = pendingConfirmationRepo.findByStatusIn(
                List.of(ConfirmationStatus.PENDING, ConfirmationStatus.FAILED, ConfirmationStatus.IN_PROGRESS));

        List<Map<String, Object>> result = pending.stream()
                .map(p -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("id", p.getId());
                    entry.put("missionId", p.getMissionId());
                    entry.put("confirmationType", p.getConfirmationType());
                    entry.put("status", p.getStatus());
                    entry.put("attemptCount", p.getAttemptCount());
                    entry.put("requestedAt", p.getRequestedAt());
                    entry.put("lastAttemptAt", p.getLastAttemptAt());
                    entry.put("reservedDistanceMm", p.getReservedDistanceMm());
                    entry.put("errorMessage", p.getErrorMessage());
                    return entry;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    /**
     * H3: System health including circuit breaker state.
     */
    @GetMapping("/health")
    @Operation(summary = "Movu Health", description = "Circuit breaker state, system state, and connectivity info")
    public ResponseEntity<Map<String, Object>> getMovuHealth() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("systemState", systemStateService.getCurrentState());

        try {
            health.put("circuitState", movuClient.getCircuitState().toString());
        } catch (Exception e) {
            health.put("circuitState", "UNKNOWN");
        }

        health.put("timestamp", java.time.Instant.now());
        return ResponseEntity.ok(health);
    }
}
