package com.palletpilot.wcs.application.service;

import com.palletpilot.wcs.domain.model.*;
import com.palletpilot.wcs.domain.model.enums.ProcessState;
import com.palletpilot.wcs.domain.model.enums.TaskStatus;
import com.palletpilot.wcs.infrastructure.persistence.repository.PalletRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.StorageLocationRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.TransportTaskRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PalletService {

        private final PalletRepository palletRepository;
        private final StorageLocationRepository storageRepository;
        private final TransportTaskRepository transportTaskRepository;
        private final SlottingOptimizationService slottingService;
        private final SlotFindingService slotFindingService;

        @Value("${slotting.enabled:true}")
        private boolean slottingEnabled;

        public List<Pallet> getAllPallets() {
                return palletRepository.findAll();
        }

        public Pallet getPallet(UUID id) {
                return palletRepository.findById(id)
                                .orElseThrow(() -> new EntityNotFoundException("Pallet not found"));
        }

        @Transactional
        public void putaway(UUID palletId) {
                Pallet pallet = getPallet(palletId);

                // Allow if READY_FOR_STORAGE or PALLETIZED
                if (pallet.getProcessState() != ProcessState.READY_FOR_STORAGE
                                && pallet.getProcessState() != ProcessState.PALLETIZED) {
                        throw new IllegalStateException(
                                        "Pallet " + palletId + " in state " + pallet.getProcessState()
                                                        + " is not ready for storage.");
                }

                StorageLocation target;

                if (slottingEnabled) {
                        // ──────────────────────────────────────────
                        // ABC/ABCD SLOTTING (production strategy)
                        // ──────────────────────────────────────────
                        String dominantSku = determineDominantSku(pallet);
                        String desiredZone = slottingService.getZoneForSku(dominantSku);

                        // Compute pallet constraints
                        Integer palletHeight = computePalletHeight(pallet);
                        Double palletWeight = pallet.getTotalWeightKg();

                        log.info("[SLOTTING] Putaway pallet {} | dominantSku={} | desiredZone={} | H={}mm W={}kg",
                                        pallet.getPalletDisplayId(), dominantSku, desiredZone, palletHeight,
                                        palletWeight);

                        target = slotFindingService.lockAndFindBestSlot(desiredZone, palletHeight, palletWeight);

                        if (target == null) {
                                throw new IllegalStateException(
                                                "Warehouse Full! No storage slots available matching pallet constraints.");
                        }

                        log.info("[SLOTTING] Pallet {} → Zone {} (Aisle {}, X={}, Y={}, Z={})",
                                        pallet.getPalletDisplayId(), target.getZoneClass(),
                                        target.getAisle(), target.getX(), target.getY(), target.getZ());
                } else {
                        // ──────────────────────────────────────────
                        // LEGACY NAIVE STRATEGY (feature flag OFF)
                        // ──────────────────────────────────────────
                        log.info("[PUTAWAY] Using legacy naive slot selection for pallet {}", palletId);
                        List<StorageLocation> emptySlots = storageRepository.findByIsOccupiedFalse();
                        if (emptySlots.isEmpty()) {
                                throw new IllegalStateException("Warehouse Full! No storage slots available.");
                        }

                        target = emptySlots.stream()
                                        .min(Comparator.comparingInt(StorageLocation::getZ)
                                                        .thenComparingInt(StorageLocation::getAisle)
                                                        .thenComparingInt(StorageLocation::getX))
                                        .orElseThrow();
                }

                // Reserve Location
                target.setOccupied(true);
                target.setStoredPalletId(pallet.getId());
                storageRepository.save(target);

                // Create Task
                TransportTask task = TransportTask.builder()
                                .palletId(pallet.getId())
                                .sourceLocationId(null) // From Inbound/Production
                                .targetLocationId(target.getId())
                                .status(TaskStatus.PENDING)
                                .build();
                transportTaskRepository.save(task);

                // Update Pallet State
                pallet.setProcessState(ProcessState.MOVING_TO_STORAGE);
                pallet.setStorageLocationId(target.getId());
                pallet.setCurrentLocation(
                                "In Transit to " + target.getZone() + " (" + target.getAisle() + "-" + target.getX()
                                                + "-" + target.getZ() + ") [Zone "
                                                + (target.getZoneClass() != null ? target.getZoneClass() : "?") + "]");
                palletRepository.save(pallet);
        }

        // ─────────────────────────────────────────────
        // HELPER: Determine dominant SKU on a pallet
        // ─────────────────────────────────────────────

        /**
         * Finds the SKU with the most boxes on a pallet.
         * For multi-SKU pallets, this determines which zone class to target.
         * Tie-breaker: SKU with higher total weight.
         */
        public String determineDominantSku(Pallet pallet) {
                if (pallet.getLayers() == null || pallet.getLayers().isEmpty()) {
                        log.warn("[SLOTTING] Pallet {} has no layers, using default zone", pallet.getPalletDisplayId());
                        return "UNKNOWN";
                }

                // Count boxes per SKU across all layers
                Map<String, Long> skuCounts = pallet.getLayers().stream()
                                .filter(l -> l.getBoxes() != null)
                                .flatMap(l -> l.getBoxes().stream())
                                .collect(Collectors.groupingBy(Box::getSku, Collectors.counting()));

                if (skuCounts.isEmpty()) {
                        return "UNKNOWN";
                }

                // Find max count; if tie, alphabetical (deterministic)
                return skuCounts.entrySet().stream()
                                .max(Map.Entry.<String, Long>comparingByValue()
                                                .thenComparing(Map.Entry::getKey))
                                .map(Map.Entry::getKey)
                                .orElse("UNKNOWN");
        }

        /**
         * Compute total pallet height from layers.
         */
        private Integer computePalletHeight(Pallet pallet) {
                if (pallet.getLayers() == null || pallet.getLayers().isEmpty()) {
                        return null; // Skip height constraint
                }

                // Sum the max z-height across layers
                int maxHeight = 0;
                for (PalletLayer layer : pallet.getLayers()) {
                        int layerTop = (layer.getZHeightMm() != null ? layer.getZHeightMm() : 0);
                        if (layer.getBoxes() != null) {
                                for (Box box : layer.getBoxes()) {
                                        int boxTop = (box.getZPosMm() != null ? box.getZPosMm() : 0)
                                                        + (box.getHeightMm() != null ? box.getHeightMm() : 0);
                                        maxHeight = Math.max(maxHeight, boxTop);
                                }
                        }
                        maxHeight = Math.max(maxHeight, layerTop);
                }

                return maxHeight > 0 ? maxHeight : null;
        }
}
