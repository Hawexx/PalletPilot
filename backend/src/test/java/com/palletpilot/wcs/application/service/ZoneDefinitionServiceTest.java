package com.palletpilot.wcs.application.service;

import com.palletpilot.wcs.domain.model.StorageLocation;
import com.palletpilot.wcs.domain.model.ZoneDefinition;
import com.palletpilot.wcs.infrastructure.persistence.repository.StorageLocationRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.ZoneDefinitionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ZoneDefinitionService — dynamic and legacy zone resolution.
 */
@ExtendWith(MockitoExtension.class)
class ZoneDefinitionServiceTest {

    @Mock
    private ZoneDefinitionRepository zoneDefRepository;
    @Mock
    private StorageLocationRepository storageRepository;

    @InjectMocks
    private ZoneDefinitionService service;

    // ── resolveZoneClass ──

    @Test
    @DisplayName("resolveZoneClass: no rules → legacy z-level mapping")
    void resolveZone_noRules_legacy() {
        when(zoneDefRepository.findByActiveTrueOrderByPriorityAsc()).thenReturn(List.of());

        StorageLocation slot = new StorageLocation();
        slot.setZ(2);

        assertThat(service.resolveZoneClass(slot)).isEqualTo("B");
    }

    @Test
    @DisplayName("resolveZoneClass: legacy z=1→A, z=3→C, z=4→D, z=5→D")
    void resolveZone_legacyMapping() {
        when(zoneDefRepository.findByActiveTrueOrderByPriorityAsc()).thenReturn(List.of());

        assertThat(service.resolveZoneClass(buildSlot(1))).isEqualTo("A");
        assertThat(service.resolveZoneClass(buildSlot(3))).isEqualTo("C");
        assertThat(service.resolveZoneClass(buildSlot(4))).isEqualTo("D");
        assertThat(service.resolveZoneClass(buildSlot(5))).isEqualTo("D"); // default
    }

    @Test
    @DisplayName("resolveZoneClass: rule matches → returns rule zone class")
    void resolveZone_ruleMatches() {
        ZoneDefinition rule = ZoneDefinition.builder()
                .zoneClass("B").priority(10).zMin(2).zMax(2).active(true).build();
        when(zoneDefRepository.findByActiveTrueOrderByPriorityAsc()).thenReturn(List.of(rule));

        StorageLocation slot = buildSlot(2);
        assertThat(service.resolveZoneClass(slot)).isEqualTo("B");
    }

    @Test
    @DisplayName("resolveZoneClass: no rule matches → defaults to D")
    void resolveZone_noRuleMatches_defaultsToD() {
        ZoneDefinition rule = ZoneDefinition.builder()
                .zoneClass("A").priority(10).zMin(1).zMax(1).active(true).build();
        when(zoneDefRepository.findByActiveTrueOrderByPriorityAsc()).thenReturn(List.of(rule));

        StorageLocation slot = buildSlot(5); // z=5, no rule matches
        assertThat(service.resolveZoneClass(slot)).isEqualTo("D");
    }

    // ── applyZoneDefinitions ──

    @Test
    @DisplayName("applyZoneDefinitions: legacy mode → updates all slots by z-level")
    void applyZones_legacyMode() {
        when(zoneDefRepository.findByActiveTrueOrderByPriorityAsc()).thenReturn(List.of());

        StorageLocation s1 = buildSlot(1);
        s1.setZoneClass("X"); // wrong zone
        StorageLocation s2 = buildSlot(2);
        s2.setZoneClass("B"); // already correct
        when(storageRepository.findAll()).thenReturn(List.of(s1, s2));

        Map<String, Object> result = service.applyZoneDefinitions();

        assertThat((int) result.get("changed")).isEqualTo(1); // only s1 changed
        verify(storageRepository).saveAll(anyList());
    }

    // ── seedDefaultDefinitions ──

    @Test
    @DisplayName("seedDefaultDefinitions: empty DB → creates 4 rules")
    void seedDefaults_emptyDb_creates4() {
        when(zoneDefRepository.count()).thenReturn(0L);

        service.seedDefaultDefinitions();

        verify(zoneDefRepository, times(4)).save(any(ZoneDefinition.class));
    }

    @Test
    @DisplayName("seedDefaultDefinitions: rules exist → skips")
    void seedDefaults_rulesExist_skips() {
        when(zoneDefRepository.count()).thenReturn(4L);

        service.seedDefaultDefinitions();

        verify(zoneDefRepository, never()).save(any());
    }

    // ── Helper ──

    private StorageLocation buildSlot(int z) {
        StorageLocation s = new StorageLocation();
        s.setZ(z);
        s.setAisle(1);
        s.setX(1);
        s.setY(1);
        return s;
    }
}
