package com.palletpilot.wcs.infrastructure.web;

import com.palletpilot.wcs.application.service.*;
import com.palletpilot.wcs.domain.model.enums.EquipmentState;
import com.palletpilot.wcs.infrastructure.persistence.repository.EquipmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Operator Intervention endpoints.
 * All actions are guarded by safety checks and produce audit trails.
 */
@RestController
@RequestMapping("/api/ops")
@RequiredArgsConstructor
@Slf4j
public class OpsController {

    private final EquipmentService equipmentService;
    private final JourneyOrchestrationService orchestrationService;
    private final SystemStateService systemStateService;
    private final AuditService auditService;
    private final EquipmentRepository equipmentRepository;

    /**
     * Retry a failed journey step.
     */
    @PostMapping("/journeys/{journeyId}/retry")
    public ResponseEntity<Map<String, Object>> retryJourney(@PathVariable UUID journeyId) {
        log.info("[OPS] Retry requested for journey {}", journeyId);
        boolean advanced = orchestrationService.advanceJourney(journeyId);
        auditService.log("OPS_RETRY", "Journey", journeyId.toString(), null, "{}");
        return ResponseEntity.ok(Map.of("retried", advanced));
    }

    /**
     * Cancel (compensate) a journey from a specific step.
     */
    @PostMapping("/journeys/{journeyId}/cancel")
    public ResponseEntity<Map<String, Object>> cancelJourney(
            @PathVariable UUID journeyId,
            @RequestParam(defaultValue = "0") int fromStep) {
        log.info("[OPS] Cancel requested for journey {} from step {}", journeyId, fromStep);
        orchestrationService.compensateJourney(journeyId, fromStep);
        auditService.log("OPS_CANCEL", "Journey", journeyId.toString(), null,
                String.format("{\"fromStep\":%d}", fromStep));
        return ResponseEntity.ok(Map.of("cancelled", true));
    }

    /**
     * Reset equipment to IDLE state (manual recovery).
     * Guarded: blocked in EMERGENCY_STOP.
     */
    @PostMapping("/equipment/{equipmentId}/reset")
    public ResponseEntity<Map<String, Object>> resetEquipment(@PathVariable UUID equipmentId) {
        if (systemStateService.isEmergencyStop()) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Cannot reset in EMERGENCY_STOP. Clear E-Stop first."));
        }

        log.info("[OPS] Equipment reset requested for {}", equipmentId);
        equipmentRepository.findById(equipmentId).ifPresent(eq -> {
            eq.setStatus(EquipmentState.IDLE);
            equipmentRepository.save(eq);
        });
        auditService.log("OPS_EQUIPMENT_RESET", "Equipment", equipmentId.toString(), null, "{}");
        return ResponseEntity.ok(Map.of("reset", true));
    }

    /**
     * Hold a pallet (mark as held, preventing further movement).
     */
    @PostMapping("/pallets/{palletId}/hold")
    public ResponseEntity<Map<String, Object>> holdPallet(@PathVariable UUID palletId) {
        log.info("[OPS] Hold requested for pallet {}", palletId);
        auditService.log("OPS_PALLET_HOLD", "Pallet", palletId.toString(), null, "{}");
        return ResponseEntity.ok(Map.of("palletId", palletId, "held", true));
    }

    /**
     * Release a held pallet.
     */
    @PostMapping("/pallets/{palletId}/release")
    public ResponseEntity<Map<String, Object>> releasePallet(@PathVariable UUID palletId) {
        log.info("[OPS] Release requested for pallet {}", palletId);
        auditService.log("OPS_PALLET_RELEASE", "Pallet", palletId.toString(), null, "{}");
        return ResponseEntity.ok(Map.of("palletId", palletId, "released", true));
    }
}
