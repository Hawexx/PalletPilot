package com.palletpilot.wcs.application.service;

import com.palletpilot.wcs.domain.model.StorageLocation;
import com.palletpilot.wcs.domain.model.ZoneDefinition;
import com.palletpilot.wcs.infrastructure.persistence.repository.StorageLocationRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.ZoneDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Manages dynamic zone definitions and applies them to storage locations.
 *
 * Two modes:
 * 1. DYNAMIC (zone_definitions table has rules): evaluate rules in priority
 * order
 * 2. LEGACY (no rules): fall back to z-level mapping (z=1→A, z=2→B, z=3→C,
 * z=4→D)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ZoneDefinitionService {

    private final ZoneDefinitionRepository zoneDefRepository;
    private final StorageLocationRepository storageRepository;

    /**
     * Resolve which zone class a storage location belongs to.
     * Evaluates rules in priority order; first match wins.
     * Falls back to z-level mapping if no rules exist.
     */
    public String resolveZoneClass(StorageLocation slot) {
        List<ZoneDefinition> rules = zoneDefRepository.findByActiveTrueOrderByPriorityAsc();

        if (rules.isEmpty()) {
            // Legacy fallback: z-level mapping
            return legacyZoneMapping(slot.getZ());
        }

        for (ZoneDefinition rule : rules) {
            if (rule.matches(slot)) {
                return rule.getZoneClass();
            }
        }

        // No rule matched → default to D (lowest priority zone)
        log.warn("[ZONE] No rule matched for slot aisle={}, x={}, y={}, z={}. Defaulting to D.",
                slot.getAisle(), slot.getX(), slot.getY(), slot.getZ());
        return "D";
    }

    /**
     * Apply zone definitions to ALL storage locations.
     * Re-evaluates every slot and updates zoneClass.
     * Returns count of changes per zone.
     */
    @Transactional
    public Map<String, Object> applyZoneDefinitions() {
        long startTime = System.currentTimeMillis();
        List<ZoneDefinition> rules = zoneDefRepository.findByActiveTrueOrderByPriorityAsc();
        List<StorageLocation> allSlots = storageRepository.findAll();

        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("A", 0);
        counts.put("B", 0);
        counts.put("C", 0);
        counts.put("D", 0);
        List<StorageLocation> changedSlots = new java.util.ArrayList<>();

        for (StorageLocation slot : allSlots) {
            String newZone;
            if (rules.isEmpty()) {
                newZone = legacyZoneMapping(slot.getZ());
            } else {
                newZone = rules.stream()
                        .filter(r -> r.matches(slot))
                        .map(ZoneDefinition::getZoneClass)
                        .findFirst()
                        .orElse("D");
            }

            if (!newZone.equals(slot.getZoneClass())) {
                slot.setZoneClass(newZone);
                changedSlots.add(slot);
            }
            counts.merge(newZone, 1, (a, b) -> a + b);
        }

        // Batch save all changed slots at once (performance optimization)
        if (!changedSlots.isEmpty()) {
            storageRepository.saveAll(changedSlots);
        }

        long durationMs = System.currentTimeMillis() - startTime;
        log.info("[ZONE] Applied zone definitions: {} slots total, {} changed, {}ms. A={}, B={}, C={}, D={}",
                allSlots.size(), changedSlots.size(), durationMs,
                counts.get("A"), counts.get("B"), counts.get("C"), counts.get("D"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalSlots", allSlots.size());
        result.put("changed", changedSlots.size());
        result.put("durationMs", durationMs);
        result.put("counts", counts);
        result.put("rulesApplied", rules.size());
        return result;
    }

    /**
     * Get all zone definitions sorted by priority.
     */
    public List<ZoneDefinition> getAllDefinitions() {
        return zoneDefRepository.findByActiveTrueOrderByPriorityAsc();
    }

    /**
     * Create or update a zone definition.
     */
    @Transactional
    public ZoneDefinition saveDefinition(ZoneDefinition definition) {
        return zoneDefRepository.save(definition);
    }

    /**
     * Delete a zone definition.
     */
    @Transactional
    public void deleteDefinition(java.util.UUID id) {
        zoneDefRepository.deleteById(id);
    }

    /**
     * Seed default zone definitions based on the standard 4-level layout.
     * Only creates rules if none exist yet.
     */
    @Transactional
    public void seedDefaultDefinitions() {
        if (zoneDefRepository.count() > 0) {
            log.debug("[ZONE] Zone definitions already exist. Skipping seed.");
            return;
        }

        log.info("[ZONE] Seeding default zone definitions (z-level based)");

        zoneDefRepository.save(ZoneDefinition.builder()
                .zoneClass("A").priority(10).label("Level 1 - Fastest Access")
                .zMin(1).zMax(1).active(true).build());

        zoneDefRepository.save(ZoneDefinition.builder()
                .zoneClass("B").priority(20).label("Level 2 - Fast Access")
                .zMin(2).zMax(2).active(true).build());

        zoneDefRepository.save(ZoneDefinition.builder()
                .zoneClass("C").priority(30).label("Level 3 - Moderate Access")
                .zMin(3).zMax(3).active(true).build());

        zoneDefRepository.save(ZoneDefinition.builder()
                .zoneClass("D").priority(40).label("Level 4 - Slowest Access")
                .zMin(4).zMax(4).active(true).build());
    }

    private String legacyZoneMapping(int z) {
        return switch (z) {
            case 1 -> "A";
            case 2 -> "B";
            case 3 -> "C";
            case 4 -> "D";
            default -> "D";
        };
    }
}
