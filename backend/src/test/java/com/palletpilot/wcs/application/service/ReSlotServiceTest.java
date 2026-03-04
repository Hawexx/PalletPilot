package com.palletpilot.wcs.application.service;

import com.palletpilot.wcs.domain.model.Pallet;
import com.palletpilot.wcs.domain.model.StorageLocation;
import com.palletpilot.wcs.domain.model.TransportTask;
import com.palletpilot.wcs.domain.model.enums.TaskStatus;
import com.palletpilot.wcs.infrastructure.persistence.repository.PalletRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.StorageLocationRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.TransportTaskRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ReSlotService — pallet re-slotting between zones.
 * Tests validation, slot swap, transport task creation, and auto re-slot.
 */
@ExtendWith(MockitoExtension.class)
class ReSlotServiceTest {

    @Mock
    private PalletRepository palletRepository;
    @Mock
    private StorageLocationRepository storageRepository;
    @Mock
    private TransportTaskRepository transportTaskRepository;
    @Mock
    private MovuService movuService;

    @InjectMocks
    private ReSlotService service;

    // ── executeReSlot ──

    @Test
    @DisplayName("executeReSlot: pallet not found → throws")
    void reSlot_palletNotFound_throws() {
        UUID palletId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        when(palletRepository.findById(palletId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.executeReSlot(palletId, targetId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Pallet not found");
    }

    @Test
    @DisplayName("executeReSlot: target not found → throws")
    void reSlot_targetNotFound_throws() {
        UUID palletId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Pallet pallet = Pallet.builder().id(palletId).build();
        when(palletRepository.findById(palletId)).thenReturn(Optional.of(pallet));
        when(storageRepository.findById(targetId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.executeReSlot(palletId, targetId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Target location not found");
    }

    @Test
    @DisplayName("executeReSlot: target occupied → throws")
    void reSlot_targetOccupied_throws() {
        UUID palletId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Pallet pallet = Pallet.builder().id(palletId).build();
        StorageLocation target = new StorageLocation();
        target.setId(targetId);
        target.setOccupied(true);

        when(palletRepository.findById(palletId)).thenReturn(Optional.of(pallet));
        when(storageRepository.findById(targetId)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.executeReSlot(palletId, targetId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already occupied");
    }

    @Test
    @DisplayName("executeReSlot: happy path → creates task, swaps slots, updates pallet")
    void reSlot_happyPath() {
        UUID palletId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        Pallet pallet = Pallet.builder()
                .id(palletId)
                .palletDisplayId("PLT-001")
                .storageLocationId(sourceId)
                .build();

        StorageLocation source = new StorageLocation();
        source.setId(sourceId);
        source.setOccupied(true);
        source.setZoneClass("B");

        StorageLocation target = new StorageLocation();
        target.setId(targetId);
        target.setOccupied(false);
        target.setZoneClass("A");
        target.setZone("ZONE-A");
        target.setAisle(1);
        target.setX(2);
        target.setY(1);
        target.setZ(3);

        ReflectionTestUtils.setField(service, "autoExecuteMove", false);
        when(palletRepository.findById(palletId)).thenReturn(Optional.of(pallet));
        when(storageRepository.findById(targetId)).thenReturn(Optional.of(target));
        when(storageRepository.findById(sourceId)).thenReturn(Optional.of(source));
        when(transportTaskRepository.save(any())).thenAnswer(inv -> {
            TransportTask t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        Map<String, Object> result = service.executeReSlot(palletId, targetId);

        assertThat(result.get("fromZone")).isEqualTo("B");
        assertThat(result.get("toZone")).isEqualTo("A");
        assertThat(result.get("status")).isEqualTo("PENDING");

        // Verify slot swap
        verify(storageRepository).save(argThat(
                loc -> ((StorageLocation) loc).getId().equals(targetId) && ((StorageLocation) loc).isOccupied()));
        verify(storageRepository).save(argThat(
                loc -> ((StorageLocation) loc).getId().equals(sourceId) && !((StorageLocation) loc).isOccupied()));
    }

    @Test
    @DisplayName("executeReSlot: autoExecuteMove=true → triggers Movu mission")
    void reSlot_autoExecute_triggersMovu() {
        UUID palletId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        Pallet pallet = Pallet.builder()
                .id(palletId)
                .palletDisplayId("PLT-002")
                .build();

        StorageLocation target = new StorageLocation();
        target.setId(targetId);
        target.setOccupied(false);
        target.setZoneClass("C");
        target.setZone("Z");
        target.setAisle(1);
        target.setX(1);
        target.setY(1);
        target.setZ(1);

        ReflectionTestUtils.setField(service, "autoExecuteMove", true);
        when(palletRepository.findById(palletId)).thenReturn(Optional.of(pallet));
        when(storageRepository.findById(targetId)).thenReturn(Optional.of(target));
        when(transportTaskRepository.save(any())).thenAnswer(inv -> {
            TransportTask t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });
        when(movuService.executeMission(contains("RESLOT-PLT-002"))).thenReturn("MSN-RESLOT");

        Map<String, Object> result = service.executeReSlot(palletId, targetId);

        assertThat(result.get("movuMissionId")).isEqualTo("MSN-RESLOT");
        assertThat(result.get("status")).isEqualTo("IN_PROGRESS");
    }

    // ── autoReSlot ──

    @Test
    @DisplayName("autoReSlot: no available slot → throws")
    void autoReSlot_noSlot_throws() {
        UUID palletId = UUID.randomUUID();
        SlotFindingService mockFinder = mock(SlotFindingService.class);
        when(mockFinder.lockAndFindBestSlot("A", null, null)).thenReturn(null);

        assertThatThrownBy(() -> service.autoReSlot(palletId, "A", mockFinder))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No available slots");
    }
}
