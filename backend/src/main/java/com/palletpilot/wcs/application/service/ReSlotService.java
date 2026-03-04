package com.palletpilot.wcs.application.service;

import com.palletpilot.wcs.domain.model.*;
import com.palletpilot.wcs.domain.model.enums.TaskStatus;
import com.palletpilot.wcs.infrastructure.persistence.repository.PalletRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.StorageLocationRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.TransportTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Handles physical re-slotting of pallets between zones.
 *
 * Flow:
 * 1. Validate pallet and target slot
 * 2. Create TransportTask (source → target)
 * 3. If Movu integration is enabled, also fire a Movu mission
 * 4. Update slot reservations (release old, reserve new)
 * 5. Update pallet location
 *
 * The actual physical move happens asynchronously via the shuttle/robot.
 * TransportTask status is updated by the Movu callback flow.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReSlotService {

    private final PalletRepository palletRepository;
    private final StorageLocationRepository storageRepository;
    private final TransportTaskRepository transportTaskRepository;
    private final MovuService movuService;

    @Value("${slotting.reslot.auto-execute:false}")
    private boolean autoExecuteMove;

    /**
     * Execute a re-slot: move a pallet from current location to a new target.
     *
     * @param palletId         The pallet to move
     * @param targetLocationId The target storage slot
     * @return Result map with task details
     */
    @Transactional
    public Map<String, Object> executeReSlot(UUID palletId, UUID targetLocationId) {
        // 1. Validate
        Pallet pallet = palletRepository.findById(palletId)
                .orElseThrow(() -> new IllegalArgumentException("Pallet not found: " + palletId));

        StorageLocation target = storageRepository.findById(targetLocationId)
                .orElseThrow(() -> new IllegalArgumentException("Target location not found: " + targetLocationId));

        if (target.isOccupied()) {
            throw new IllegalStateException("Target location is already occupied");
        }

        UUID sourceLocationId = pallet.getStorageLocationId();
        StorageLocation source = null;
        if (sourceLocationId != null) {
            source = storageRepository.findById(sourceLocationId).orElse(null);
        }

        log.info("[RESLOT] Moving pallet {} from {} to {} (zone {} → {})",
                pallet.getPalletDisplayId(),
                source != null ? formatLocation(source) : "UNKNOWN",
                formatLocation(target),
                source != null ? source.getZoneClass() : "?",
                target.getZoneClass());

        // 2. Create TransportTask
        TransportTask task = TransportTask.builder()
                .palletId(palletId)
                .sourceLocationId(sourceLocationId)
                .targetLocationId(targetLocationId)
                .status(TaskStatus.PENDING)
                .build();
        transportTaskRepository.save(task);

        // 3. Reserve new slot, release old slot
        target.setOccupied(true);
        target.setStoredPalletId(palletId);
        storageRepository.save(target);

        if (source != null) {
            source.setOccupied(false);
            source.setStoredPalletId(null);
            storageRepository.save(source);
        }

        // 4. Update pallet location
        pallet.setStorageLocationId(targetLocationId);
        pallet.setCurrentLocation("Re-slotting to " + target.getZone()
                + " (" + target.getAisle() + "-" + target.getX() + "-" + target.getZ()
                + ") [Zone " + target.getZoneClass() + "]");
        palletRepository.save(pallet);

        // 5. Trigger Movu mission if auto-execute enabled
        String movuMissionId = null;
        if (autoExecuteMove) {
            try {
                // Determine dominant SKU for mission execution
                String dominantSku = "RESLOT-" + pallet.getPalletDisplayId();
                movuMissionId = movuService.executeMission(dominantSku);
                task.setStatus(TaskStatus.IN_PROGRESS);
                transportTaskRepository.save(task);
                log.info("[RESLOT] Movu mission triggered: {}", movuMissionId);
            } catch (Exception e) {
                log.warn("[RESLOT] Movu mission failed (will remain PENDING for manual execution): {}",
                        e.getMessage());
            }
        }

        return Map.of(
                "taskId", task.getId().toString(),
                "palletId", palletId.toString(),
                "palletDisplayId", pallet.getPalletDisplayId() != null ? pallet.getPalletDisplayId() : "?",
                "fromZone", source != null && source.getZoneClass() != null ? source.getZoneClass() : "?",
                "toZone", target.getZoneClass() != null ? target.getZoneClass() : "?",
                "targetLocation", formatLocation(target),
                "status", task.getStatus().toString(),
                "movuMissionId", movuMissionId != null ? movuMissionId : "N/A");
    }

    /**
     * Auto re-slot: find the best available slot in the recommended zone and move.
     */
    @Transactional
    public Map<String, Object> autoReSlot(UUID palletId, String targetZone, SlotFindingService slotFinder) {
        StorageLocation bestSlot = slotFinder.lockAndFindBestSlot(targetZone, null, null);
        if (bestSlot == null) {
            throw new IllegalStateException("No available slots in zone " + targetZone + " (or overflow zones)");
        }
        return executeReSlot(palletId, bestSlot.getId());
    }

    private String formatLocation(StorageLocation loc) {
        return loc.getZone() + " A" + loc.getAisle() + "-X" + loc.getX()
                + "-Y" + loc.getY() + "-Z" + loc.getZ();
    }
}
