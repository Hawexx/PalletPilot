package com.palletpilot.wcs.application.service;

import com.palletpilot.wcs.domain.model.Equipment;
import com.palletpilot.wcs.domain.model.MovuLoadPosition;
import com.palletpilot.wcs.domain.model.MovuMission;
import com.palletpilot.wcs.domain.model.enums.EquipmentType;
import com.palletpilot.wcs.infrastructure.movu.MovuClient;
import com.palletpilot.wcs.infrastructure.persistence.repository.EquipmentRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.MovuLoadPositionRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.MovuMissionRepository;
import com.palletpilot.wcs.infrastructure.wms.WmsClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MovuService {

    private final WmsClient wmsClient;
    private final MovuClient movuClient;
    private final PlcService plcService;
    private final MovuMissionRepository missionRepository;
    private final MovuLoadPositionRepository loadPositionRepository;
    private final EquipmentRepository equipmentRepository;

    @Transactional
    public String executeMission(String sku) {
        log.info("Initiating Movu Mission for SKU: {}", sku);

        // 1. WMS Lookup
        String sourceLocation = wmsClient.getPalletLocation(sku);
        if (sourceLocation == null || sourceLocation.isEmpty()) {
            throw new IllegalArgumentException("SKU location not found in WMS: " + sku);
        }

        // 2. Determine Scope
        Equipment movu = findMovuEquipment();
        String targetLocation = "CONVEYOR-IN-1"; // Hardcoded handoff point for now

        // 3. Build mission payload
        Map<String, Object> payload = new HashMap<>();
        payload.put("source", sourceLocation);
        payload.put("target", targetLocation);
        payload.put("sku", sku);

        // Phase B: Load Position Memory — use actual distance from last drop if
        // available
        Optional<MovuLoadPosition> lastPosition = loadPositionRepository.findById(sku);
        if (lastPosition.isPresent()) {
            MovuLoadPosition pos = lastPosition.get();
            payload.put("sourceDistance", pos.getDistanceMm());
            log.info("Using known load position for SKU {}: distance={}mm (from mission {})",
                    sku, pos.getDistanceMm(), pos.getLastMissionId());
        }

        // 4. Command Movu (Async)
        String missionId = movuClient.createMission(movu.getId(), "PICK_DROP", payload);

        // 5. Persist State
        MovuMission mission = MovuMission.builder()
                .id(missionId)
                .equipmentId(movu.getId())
                .sku(sku)
                .sourceLocation(sourceLocation)
                .targetLocation(targetLocation)
                .status("CREATED") // Use proper initial status (was "RUNNING")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        missionRepository.save(mission);
        log.info("Movu Mission Started: {}", missionId);

        return missionId;
    }

    /**
     * Legacy callback handler — maintained for backward compatibility.
     * The primary flow now uses MovuEventIngestService via the refactored
     * controller.
     *
     * @deprecated Use MovuEventIngestService.ingest() instead
     */
    @Deprecated
    @Transactional
    public void handleCallback(String missionId, String status) {
        log.info("Legacy callback for Mission {}: {}", missionId, status);

        missionRepository.findById(missionId).ifPresent(mission -> {
            mission.setStatus(status);
            mission.setUpdatedAt(Instant.now());
            missionRepository.save(mission);

            if ("COMPLETED".equalsIgnoreCase(status)) {
                log.info("Mission {} Completed. Triggering Conveyor Handoff.", missionId);
                triggerHandoff(mission.getTargetLocation());
            } else if ("FAILED".equalsIgnoreCase(status)) {
                log.error("Mission {} Failed! Manual intervention required.", missionId);
            }
        });
    }

    /**
     * Trigger conveyor handoff after mission completion.
     * Called by MovuEventIngestService when MissionDropped is received.
     */
    public void triggerHandoff(String targetLocation) {
        Equipment conveyor = equipmentRepository.findByName("CONVEYOR-IN-1")
                .orElseThrow(() -> new IllegalStateException(
                        "Handoff Conveyor 'CONVEYOR-IN-1' not found. Check configuration."));

        log.info("Starting Conveyor {} for handoff", conveyor.getName());
        plcService.startConveyor(conveyor.getId(), 50); // Speed 50
    }

    private Equipment findMovuEquipment() {
        return equipmentRepository.findFirstByType(EquipmentType.MOVU)
                .orElseThrow(() -> new IllegalStateException("No MOVU equipment configured"));
    }
}
