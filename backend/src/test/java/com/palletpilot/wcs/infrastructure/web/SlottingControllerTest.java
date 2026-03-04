package com.palletpilot.wcs.infrastructure.web;

import com.palletpilot.wcs.application.service.*;
import com.palletpilot.wcs.domain.model.SkuAnalytics;
import com.palletpilot.wcs.domain.model.ZoneDefinition;
import com.palletpilot.wcs.infrastructure.persistence.repository.PalletRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.StorageLocationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SlottingController.
 * Tests analytics, reslot validation, zone definition CRUD, and zone summary
 * computation.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class SlottingControllerTest {

    @Mock
    private SlottingOptimizationService slottingService;
    @Mock
    private StorageLocationRepository storageRepository;
    @Mock
    private PalletRepository palletRepository;
    @Mock
    private PalletService palletService;
    @Mock
    private ReSlotService reSlotService;
    @Mock
    private SlotFindingService slotFindingService;
    @Mock
    private ZoneDefinitionService zoneDefinitionService;

    @InjectMocks
    private SlottingController controller;

    // ── Analytics ──

    @Test
    @DisplayName("getAnalytics → returns all SKU analytics")
    void getAnalytics_returns200() {
        SkuAnalytics a = SkuAnalytics.builder().sku("SKU-A").zoneClass("A").build();
        when(slottingService.getAllAnalytics()).thenReturn(List.of(a));

        ResponseEntity<List<SkuAnalytics>> response = controller.getAnalytics();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    @DisplayName("getAnalyticsForSku → 404 when SKU not found")
    void getAnalyticsForSku_notFound() {
        when(slottingService.getAnalyticsForSku("UNKNOWN")).thenReturn(Optional.empty());

        ResponseEntity<SkuAnalytics> response = controller.getAnalyticsForSku("UNKNOWN");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("recalculate → delegates with periodDays and returns result")
    void recalculate_returns200() {
        when(slottingService.recalculateSkuAnalytics(60)).thenReturn(
                Map.of("recalculated", 150));

        ResponseEntity<Map<String, Object>> response = controller.recalculate(60);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("recalculated")).isEqualTo(150);
    }

    // ── Zone Summary ──

    @Test
    @DisplayName("getZoneSummary → computes occupancy for ABCD zones")
    void getZoneSummary_computesOccupancy() {
        // Zone A: 10 total, 5 occupied → 50%
        when(storageRepository.countByZoneClass("A")).thenReturn(10L);
        when(storageRepository.countByZoneClassAndIsOccupied("A", true)).thenReturn(5L);
        // Zones B,C,D: empty
        when(storageRepository.countByZoneClass(argThat(z -> !"A".equals(z)))).thenReturn(0L);
        when(storageRepository.countByZoneClassAndIsOccupied(argThat(z -> !"A".equals(z)), eq(true)))
                .thenReturn(0L);

        var response = controller.getZoneSummary();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(4);
    }

    // ── Reslot ──

    @Test
    @DisplayName("reslot → 400 when palletId missing")
    void reslot_missingPalletId_returns400() {
        ResponseEntity<Map<String, Object>> response = controller.reslot(
                Map.of("targetLocationId", UUID.randomUUID().toString()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("reslot → 202 on successful reslot")
    void reslot_success_returns202() {
        UUID palletId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        when(reSlotService.executeReSlot(palletId, targetId))
                .thenReturn(Map.of("status", "INITIATED"));

        ResponseEntity<Map<String, Object>> response = controller.reslot(
                Map.of("palletId", palletId.toString(), "targetLocationId", targetId.toString()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    @Test
    @DisplayName("autoReslot → 400 when missing targetZone")
    void autoReslot_missingZone_returns400() {
        ResponseEntity<Map<String, Object>> response = controller.autoReslot(
                Map.of("palletId", UUID.randomUUID().toString()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── Zone CRUD ──

    @Test
    @DisplayName("deleteZoneDefinition → 204 No Content")
    void deleteZone_returns204() {
        UUID id = UUID.randomUUID();

        ResponseEntity<Void> response = controller.deleteZoneDefinition(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(zoneDefinitionService).deleteDefinition(id);
    }

    @Test
    @DisplayName("applyZoneDefinitions → delegates and returns result map")
    void applyZones_returns200() {
        when(zoneDefinitionService.applyZoneDefinitions())
                .thenReturn(Map.of("updatedSlots", 250));

        ResponseEntity<Map<String, Object>> response = controller.applyZoneDefinitions();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("updatedSlots")).isEqualTo(250);
    }
}
