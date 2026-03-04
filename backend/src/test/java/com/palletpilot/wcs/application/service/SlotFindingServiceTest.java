package com.palletpilot.wcs.application.service;

import com.palletpilot.wcs.domain.model.StorageLocation;
import com.palletpilot.wcs.infrastructure.persistence.repository.StorageLocationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SlotFindingService — zone-based slot allocation
 * with overflow cascade and row-locking.
 */
@ExtendWith(MockitoExtension.class)
class SlotFindingServiceTest {

    @Mock
    private StorageLocationRepository storageRepository;

    @InjectMocks
    private SlotFindingService service;

    @Test
    @DisplayName("desired zone A has slot → returns it, no overflow")
    void findSlot_desiredZone_available() {
        StorageLocation slot = buildSlot("A", 1, 1, 1);
        when(storageRepository.lockBestEmptySlot("A", 1500, 800.0)).thenReturn(slot);

        StorageLocation result = service.lockAndFindBestSlot("A", 1500, 800.0);

        assertThat(result).isNotNull();
        assertThat(result.getZoneClass()).isEqualTo("A");
        verify(storageRepository, times(1)).lockBestEmptySlot(any(), any(), any());
    }

    @Test
    @DisplayName("zone A full → overflows to B")
    void findSlot_zoneAFull_overflowsToB() {
        when(storageRepository.lockBestEmptySlot("A", 1500, 800.0)).thenReturn(null);
        StorageLocation slotB = buildSlot("B", 2, 1, 1);
        when(storageRepository.lockBestEmptySlot("B", 1500, 800.0)).thenReturn(slotB);

        StorageLocation result = service.lockAndFindBestSlot("A", 1500, 800.0);

        assertThat(result).isNotNull();
        assertThat(result.getZoneClass()).isEqualTo("B");
    }

    @Test
    @DisplayName("zones C, D full → reverse overflow to A, B")
    void findSlot_forwardFull_reverseOverflow() {
        // Desired: C, but C and D are full
        when(storageRepository.lockBestEmptySlot("C", null, null)).thenReturn(null);
        when(storageRepository.lockBestEmptySlot("D", null, null)).thenReturn(null);
        // Reverse: A has slot
        StorageLocation slotA = buildSlot("A", 1, 1, 1);
        when(storageRepository.lockBestEmptySlot("A", null, null)).thenReturn(slotA);

        StorageLocation result = service.lockAndFindBestSlot("C", null, null);

        assertThat(result).isNotNull();
        assertThat(result.getZoneClass()).isEqualTo("A");
    }

    @Test
    @DisplayName("ALL zones full → returns null (warehouse full)")
    void findSlot_allZonesFull_returnsNull() {
        when(storageRepository.lockBestEmptySlot(anyString(), isNull(), isNull())).thenReturn(null);

        StorageLocation result = service.lockAndFindBestSlot("B", null, null);

        assertThat(result).isNull();
        // Should have tried all 4 zones: B, C, D (forward) + A (reverse)
        verify(storageRepository, times(4)).lockBestEmptySlot(anyString(), isNull(), isNull());
    }

    @Test
    @DisplayName("unknown zone → defaults to start from C")
    void findSlot_unknownZone_defaultsToC() {
        StorageLocation slotC = buildSlot("C", 3, 1, 1);
        when(storageRepository.lockBestEmptySlot("C", null, null)).thenReturn(slotC);

        StorageLocation result = service.lockAndFindBestSlot("X", null, null);

        assertThat(result).isNotNull();
        assertThat(result.getZoneClass()).isEqualTo("C");
    }

    @Test
    @DisplayName("desired zone D (last) full → reverse tries A, B, C")
    void findSlot_zoneDFull_reverseAll() {
        when(storageRepository.lockBestEmptySlot("D", null, null)).thenReturn(null);
        StorageLocation slotB = buildSlot("B", 2, 1, 1);
        when(storageRepository.lockBestEmptySlot("A", null, null)).thenReturn(null);
        when(storageRepository.lockBestEmptySlot("B", null, null)).thenReturn(slotB);

        StorageLocation result = service.lockAndFindBestSlot("D", null, null);

        assertThat(result).isNotNull();
        assertThat(result.getZoneClass()).isEqualTo("B");
    }

    private StorageLocation buildSlot(String zone, int aisle, int x, int y) {
        StorageLocation loc = new StorageLocation();
        loc.setZoneClass(zone);
        loc.setAisle(aisle);
        loc.setX(x);
        loc.setY(y);
        loc.setZ(1);
        return loc;
    }
}
