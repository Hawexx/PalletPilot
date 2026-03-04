package com.palletpilot.wcs.infrastructure.web;

import com.palletpilot.wcs.application.dto.ReSlottingRecommendation;
import com.palletpilot.wcs.application.dto.ZoneSummary;
import com.palletpilot.wcs.application.service.*;
import com.palletpilot.wcs.domain.model.*;
import com.palletpilot.wcs.infrastructure.persistence.repository.PalletRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.StorageLocationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.*;

/**
 * REST controller for ABC/ABCD slotting optimization.
 * Provides analytics, zone summaries, recommendations, re-slotting,
 * and zone definition management.
 */
@RestController
@Tag(name = "ABC Slotting")
@RequestMapping("/api/slotting")
@RequiredArgsConstructor
@Slf4j
public class SlottingController {

    private final SlottingOptimizationService slottingService;
    private final StorageLocationRepository storageRepository;
    private final PalletRepository palletRepository;
    private final PalletService palletService;
    private final ReSlotService reSlotService;
    private final SlotFindingService slotFindingService;
    private final ZoneDefinitionService zoneDefinitionService;

    // ═══════════════════════════════════════════════
    // ANALYTICS
    // ═══════════════════════════════════════════════

    @GetMapping("/analytics")
    public ResponseEntity<List<SkuAnalytics>> getAnalytics() {
        return ResponseEntity.ok(slottingService.getAllAnalytics());
    }

    @GetMapping("/analytics/{sku}")
    public ResponseEntity<SkuAnalytics> getAnalyticsForSku(@PathVariable String sku) {
        return slottingService.getAnalyticsForSku(sku)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/recalculate")
    public ResponseEntity<Map<String, Object>> recalculate(
            @RequestParam(defaultValue = "30") int periodDays) {
        log.info("[SLOTTING API] Recalculate triggered for {} days", periodDays);
        return ResponseEntity.ok(slottingService.recalculateSkuAnalytics(periodDays));
    }

    // ═══════════════════════════════════════════════
    // ZONE SUMMARY
    // ═══════════════════════════════════════════════

    @GetMapping("/zone-summary")
    public ResponseEntity<List<ZoneSummary>> getZoneSummary() {
        List<ZoneSummary> summaries = new ArrayList<>();
        for (String zone : List.of("A", "B", "C", "D")) {
            long total = storageRepository.countByZoneClass(zone);
            long occupied = storageRepository.countByZoneClassAndIsOccupied(zone, true);
            double pct = total > 0 ? (double) occupied / total * 100.0 : 0.0;

            summaries.add(ZoneSummary.builder()
                    .zoneClass(zone)
                    .totalSlots(total)
                    .occupiedSlots(occupied)
                    .occupancyPercent(Math.round(pct * 10.0) / 10.0)
                    .build());
        }
        return ResponseEntity.ok(summaries);
    }

    // ═══════════════════════════════════════════════
    // RECOMMENDATIONS
    // ═══════════════════════════════════════════════

    @GetMapping("/recommendations")
    public ResponseEntity<List<ReSlottingRecommendation>> getRecommendations() {
        List<ReSlottingRecommendation> recommendations = new ArrayList<>();
        List<StorageLocation> occupiedSlots = storageRepository.findByIsOccupiedTrue();

        for (StorageLocation slot : occupiedSlots) {
            if (slot.getStoredPalletId() == null || slot.getZoneClass() == null)
                continue;

            Pallet pallet = palletRepository.findById(slot.getStoredPalletId()).orElse(null);
            if (pallet == null)
                continue;

            String dominantSku = palletService.determineDominantSku(pallet);
            if ("UNKNOWN".equals(dominantSku))
                continue;

            String expectedZone = slottingService.getZoneForSku(dominantSku);
            String currentZone = slot.getZoneClass();

            if (!expectedZone.equals(currentZone)) {
                int currentRank = zoneRank(currentZone);
                int expectedRank = zoneRank(expectedZone);
                double priorityScore = Math.abs(currentRank - expectedRank) * 10.0;

                String reason = String.format(
                        "SKU '%s' classified as Zone %s but stored in Zone %s (Aisle %d, X=%d, Z=%d)",
                        dominantSku, expectedZone, currentZone, slot.getAisle(), slot.getX(), slot.getZ());

                recommendations.add(ReSlottingRecommendation.builder()
                        .palletId(pallet.getId())
                        .palletDisplayId(pallet.getPalletDisplayId())
                        .sku(dominantSku)
                        .currentZone(currentZone)
                        .expectedZone(expectedZone)
                        .currentLocationId(slot.getId())
                        .priorityScore(priorityScore)
                        .reason(reason)
                        .build());
            }
        }

        recommendations.sort(Comparator.comparingDouble(ReSlottingRecommendation::getPriorityScore).reversed());
        return ResponseEntity.ok(recommendations);
    }

    // ═══════════════════════════════════════════════
    // RE-SLOTTING (Physical Move via ReSlotService)
    // ═══════════════════════════════════════════════

    /**
     * POST /api/slotting/reslot
     * Move a pallet to a specific target location.
     * Body: { "palletId": "...", "targetLocationId": "..." }
     */
    @PostMapping("/reslot")
    public ResponseEntity<Map<String, Object>> reslot(@RequestBody Map<String, String> body) {
        String palletIdStr = body.get("palletId");
        String targetLocationIdStr = body.get("targetLocationId");

        if (palletIdStr == null || targetLocationIdStr == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "palletId and targetLocationId required"));
        }

        try {
            Map<String, Object> result = reSlotService.executeReSlot(
                    UUID.fromString(palletIdStr), UUID.fromString(targetLocationIdStr));
            return ResponseEntity.accepted().body(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/slotting/reslot/auto
     * Automatically find the best slot in the target zone and move the pallet
     * there.
     * Body: { "palletId": "...", "targetZone": "A" }
     */
    @PostMapping("/reslot/auto")
    public ResponseEntity<Map<String, Object>> autoReslot(@RequestBody Map<String, String> body) {
        String palletIdStr = body.get("palletId");
        String targetZone = body.get("targetZone");

        if (palletIdStr == null || targetZone == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "palletId and targetZone required"));
        }

        try {
            Map<String, Object> result = reSlotService.autoReSlot(
                    UUID.fromString(palletIdStr), targetZone, slotFindingService);
            return ResponseEntity.accepted().body(result);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════
    // ZONE DEFINITIONS (Dynamic Zone Boundaries)
    // ═══════════════════════════════════════════════

    /** GET /api/slotting/zones — list all zone definition rules */
    @GetMapping("/zones")
    public ResponseEntity<List<ZoneDefinition>> getZoneDefinitions() {
        return ResponseEntity.ok(zoneDefinitionService.getAllDefinitions());
    }

    /** POST /api/slotting/zones — create/update a zone definition rule */
    @PostMapping("/zones")
    public ResponseEntity<ZoneDefinition> saveZoneDefinition(@RequestBody ZoneDefinition definition) {
        return ResponseEntity.ok(zoneDefinitionService.saveDefinition(definition));
    }

    /** DELETE /api/slotting/zones/{id} — delete a zone definition rule */
    @DeleteMapping("/zones/{id}")
    public ResponseEntity<Void> deleteZoneDefinition(@PathVariable UUID id) {
        zoneDefinitionService.deleteDefinition(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/slotting/zones/apply
     * Apply current zone definitions to all storage locations.
     * Re-evaluates every slot's zoneClass based on active rules.
     */
    @PostMapping("/zones/apply")
    public ResponseEntity<Map<String, Object>> applyZoneDefinitions() {
        log.info("[SLOTTING API] Applying zone definitions to all storage locations");
        return ResponseEntity.ok(zoneDefinitionService.applyZoneDefinitions());
    }

    private int zoneRank(String zone) {
        return switch (zone) {
            case "A" -> 1;
            case "B" -> 2;
            case "C" -> 3;
            case "D" -> 4;
            default -> 3;
        };
    }
}
