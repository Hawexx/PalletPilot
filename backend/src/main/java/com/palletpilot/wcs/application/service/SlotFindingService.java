package com.palletpilot.wcs.application.service;

import com.palletpilot.wcs.domain.model.StorageLocation;
import com.palletpilot.wcs.infrastructure.persistence.repository.StorageLocationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Finds the best storage slot for a pallet based on zone classification.
 * Uses DB-level row locking (FOR UPDATE SKIP LOCKED) for concurrency safety.
 *
 * Overflow strategy: if desired zone is full, tries next zone in chain:
 * A → B → C → D
 *
 * Multi-deep rule: prefers y=1 (front) over y=2 (back) via ORDER BY in query.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SlotFindingService {

    private final StorageLocationRepository storageRepository;

    // Zone overflow chain: try desired zone first, then cascade
    private static final List<String> ZONE_ORDER = List.of("A", "B", "C", "D");

    /**
     * Find and lock the best available slot for a pallet.
     * This method MUST be called within @Transactional context.
     *
     * @param desiredZone  The ideal zone (A/B/C/D) based on SKU demand
     * @param palletHeight Pallet total height in mm (null to skip constraint)
     * @param palletWeight Pallet total weight in kg (null to skip constraint)
     * @return The locked StorageLocation, or null if no slot available in any zone
     */
    @Transactional
    public StorageLocation lockAndFindBestSlot(String desiredZone, Integer palletHeight, Double palletWeight) {
        // Build overflow chain starting from desired zone
        int startIdx = ZONE_ORDER.indexOf(desiredZone);
        if (startIdx < 0) {
            log.warn("[SLOTTING] Unknown zone '{}', defaulting overflow from C", desiredZone);
            startIdx = 2; // C
        }

        for (int i = startIdx; i < ZONE_ORDER.size(); i++) {
            String zone = ZONE_ORDER.get(i);
            StorageLocation slot = storageRepository.lockBestEmptySlot(zone, palletHeight, palletWeight);

            if (slot != null) {
                if (!zone.equals(desiredZone)) {
                    log.warn("[SLOTTING] Zone {} full. Overflow to zone {} for pallet (H={}mm, W={}kg)",
                            desiredZone, zone, palletHeight, palletWeight);
                }
                log.info("[SLOTTING] Selected slot: zone={}, aisle={}, x={}, y={}, z={} (desired={})",
                        zone, slot.getAisle(), slot.getX(), slot.getY(), slot.getZ(), desiredZone);
                return slot;
            }
        }

        // Also try zones ABOVE the desired zone (A/B if desired was C)
        // This is a safety net for edge cases
        for (int i = 0; i < startIdx; i++) {
            String zone = ZONE_ORDER.get(i);
            StorageLocation slot = storageRepository.lockBestEmptySlot(zone, palletHeight, palletWeight);
            if (slot != null) {
                log.warn("[SLOTTING] All lower/equal zones full. Reverse overflow to zone {} for desired {}",
                        zone, desiredZone);
                return slot;
            }
        }

        log.error("[SLOTTING] WAREHOUSE FULL — No available slots in any zone matching constraints (H={}mm, W={}kg)",
                palletHeight, palletWeight);
        return null;
    }
}
