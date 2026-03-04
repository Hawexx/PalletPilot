package com.palletpilot.wcs.application.service;

import com.palletpilot.wcs.domain.model.*;
import com.palletpilot.wcs.domain.model.enums.ProcessState;
import com.palletpilot.wcs.domain.model.enums.TaskStatus;
import com.palletpilot.wcs.infrastructure.persistence.repository.PalletRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.StorageLocationRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.TransportTaskRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PalletService — putaway orchestration,
 * dominant SKU determination, and pallet height computation.
 */
@ExtendWith(MockitoExtension.class)
class PalletServiceTest {

    @Mock
    private PalletRepository palletRepository;
    @Mock
    private StorageLocationRepository storageRepository;
    @Mock
    private TransportTaskRepository transportTaskRepository;
    @Mock
    private SlottingOptimizationService slottingService;
    @Mock
    private SlotFindingService slotFindingService;

    @InjectMocks
    private PalletService service;

    // ── determineDominantSku ──

    @Test
    @DisplayName("determineDominantSku: no layers → UNKNOWN")
    void dominantSku_noLayers_returnsUnknown() {
        Pallet pallet = Pallet.builder().layers(List.of()).build();
        assertThat(service.determineDominantSku(pallet)).isEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("determineDominantSku: null layers → UNKNOWN")
    void dominantSku_nullLayers_returnsUnknown() {
        Pallet pallet = Pallet.builder().build();
        assertThat(service.determineDominantSku(pallet)).isEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("determineDominantSku: single SKU → returns it")
    void dominantSku_singleSku() {
        Box b1 = Box.builder().sku("SKU-A").build();
        Box b2 = Box.builder().sku("SKU-A").build();
        PalletLayer layer = PalletLayer.builder().boxes(List.of(b1, b2)).build();
        Pallet pallet = Pallet.builder().layers(List.of(layer)).build();

        assertThat(service.determineDominantSku(pallet)).isEqualTo("SKU-A");
    }

    @Test
    @DisplayName("determineDominantSku: multi-SKU → returns highest count")
    void dominantSku_multiSku_highestCount() {
        Box a1 = Box.builder().sku("SKU-A").build();
        Box b1 = Box.builder().sku("SKU-B").build();
        Box b2 = Box.builder().sku("SKU-B").build();
        PalletLayer layer = PalletLayer.builder().boxes(List.of(a1, b1, b2)).build();
        Pallet pallet = Pallet.builder().layers(List.of(layer)).build();

        assertThat(service.determineDominantSku(pallet)).isEqualTo("SKU-B");
    }

    // ── putaway ──

    @Test
    @DisplayName("putaway: pallet not READY_FOR_STORAGE → throws")
    void putaway_wrongState_throws() {
        UUID id = UUID.randomUUID();
        Pallet pallet = Pallet.builder().id(id).processState(ProcessState.CREATED).build();
        when(palletRepository.findById(id)).thenReturn(Optional.of(pallet));

        assertThatThrownBy(() -> service.putaway(id))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not ready for storage");
    }

    @Test
    @DisplayName("putaway: pallet not found → throws EntityNotFoundException")
    void putaway_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(palletRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.putaway(id))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("putaway: slotting enabled, slot found → reserves slot and creates task")
    void putaway_slottingEnabled_happyPath() {
        UUID palletId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();

        Box b = Box.builder().sku("SKU-A").build();
        PalletLayer layer = PalletLayer.builder().boxes(List.of(b)).build();
        Pallet pallet = Pallet.builder()
                .id(palletId)
                .processState(ProcessState.READY_FOR_STORAGE)
                .layers(List.of(layer))
                .build();

        StorageLocation slot = new StorageLocation();
        slot.setId(slotId);
        slot.setZoneClass("A");
        slot.setZone("ZONE-A");
        slot.setAisle(1);
        slot.setX(1);
        slot.setY(1);
        slot.setZ(1);

        ReflectionTestUtils.setField(service, "slottingEnabled", true);
        when(palletRepository.findById(palletId)).thenReturn(Optional.of(pallet));
        when(slottingService.getZoneForSku("SKU-A")).thenReturn("A");
        when(slotFindingService.lockAndFindBestSlot(eq("A"), any(), any())).thenReturn(slot);

        service.putaway(palletId);

        verify(storageRepository).save(argThat(loc -> ((StorageLocation) loc).isOccupied()
                && palletId.equals(((StorageLocation) loc).getStoredPalletId())));
        verify(transportTaskRepository).save(argThat(t -> ((TransportTask) t).getStatus() == TaskStatus.PENDING));
        verify(palletRepository).save(argThat(p -> ((Pallet) p).getProcessState() == ProcessState.MOVING_TO_STORAGE));
    }

    @Test
    @DisplayName("putaway: slotting enabled, warehouse full → throws")
    void putaway_slottingEnabled_warehouseFull() {
        UUID palletId = UUID.randomUUID();
        Pallet pallet = Pallet.builder()
                .id(palletId)
                .processState(ProcessState.READY_FOR_STORAGE)
                .layers(List.of())
                .build();

        ReflectionTestUtils.setField(service, "slottingEnabled", true);
        when(palletRepository.findById(palletId)).thenReturn(Optional.of(pallet));
        when(slottingService.getZoneForSku("UNKNOWN")).thenReturn("C");
        when(slotFindingService.lockAndFindBestSlot(eq("C"), any(), any())).thenReturn(null);

        assertThatThrownBy(() -> service.putaway(palletId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Warehouse Full");
    }

    @Test
    @DisplayName("putaway: PALLETIZED state is also allowed")
    void putaway_palletizedState_allowed() {
        UUID palletId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();

        Pallet pallet = Pallet.builder()
                .id(palletId)
                .processState(ProcessState.PALLETIZED)
                .layers(List.of())
                .build();

        StorageLocation slot = new StorageLocation();
        slot.setId(slotId);
        slot.setZone("X");
        slot.setZoneClass("C");
        slot.setAisle(1);
        slot.setX(1);
        slot.setY(1);
        slot.setZ(1);

        ReflectionTestUtils.setField(service, "slottingEnabled", true);
        when(palletRepository.findById(palletId)).thenReturn(Optional.of(pallet));
        when(slottingService.getZoneForSku("UNKNOWN")).thenReturn("C");
        when(slotFindingService.lockAndFindBestSlot(eq("C"), any(), any())).thenReturn(slot);

        service.putaway(palletId);

        verify(palletRepository).save(argThat(p -> ((Pallet) p).getProcessState() == ProcessState.MOVING_TO_STORAGE));
    }
}
