package com.palletpilot.wcs.infrastructure.persistence;

import com.palletpilot.wcs.application.service.ZoneDefinitionService;
import com.palletpilot.wcs.infrastructure.persistence.repository.ChargingStationRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.RobotRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.StorageLocationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for LayoutSeeder — warehouse seeding logic.
 * Tests: skip-when-data-exists, seed-when-empty, zone class (z->zoneClass),
 * creates 4 charging stations + 4 robots.
 */
@ExtendWith(MockitoExtension.class)
class LayoutSeederTest {

    @Mock
    private StorageLocationRepository storageRepository;
    @Mock
    private RobotRepository robotRepository;
    @Mock
    private ChargingStationRepository stationRepository;
    @Mock
    private ZoneDefinitionService zoneDefinitionService;
    @InjectMocks
    private LayoutSeeder seeder;

    @Test
    @DisplayName("run: data exists → skips seeding (only seeds zone definitions)")
    void run_dataExists_skips() throws Exception {
        when(storageRepository.count()).thenReturn(100L);

        seeder.run();

        verify(zoneDefinitionService).seedDefaultDefinitions();
        verify(storageRepository, never()).save(any());
        verify(robotRepository, never()).save(any());
        verify(stationRepository, never()).save(any());
    }

    @Test
    @DisplayName("run: empty DB → seeds locations, chargers, robots")
    void run_emptyDb_seeds() throws Exception {
        when(storageRepository.count()).thenReturn(0L);

        seeder.run();

        // 4 aisles × 20 bays × 4 levels × 2 depths = 640 locations
        verify(storageRepository, times(640)).save(any());
        // 4 charging stations
        verify(stationRepository, times(4)).save(any());
        // 4 robots
        verify(robotRepository, times(4)).save(any());
    }

    @Test
    @DisplayName("run: always seeds zone definitions (idempotent)")
    void run_alwaysSeedsZoneDefinitions() throws Exception {
        when(storageRepository.count()).thenReturn(500L);

        seeder.run();

        verify(zoneDefinitionService).seedDefaultDefinitions();
    }

    @Test
    @DisplayName("run: zone class mapping — z=1→A, z=2→B, z=3→C, z=4→D")
    void run_zoneClassMapping() throws Exception {
        when(storageRepository.count()).thenReturn(0L);

        seeder.run();

        // Verify zone class A exists (z=1 locations)
        verify(storageRepository, atLeast(1)).save(argThat(loc -> "A".equals(loc.getZoneClass()) && loc.getZ() == 1));
        // Verify zone class D exists (z=4 locations)
        verify(storageRepository, atLeast(1)).save(argThat(loc -> "D".equals(loc.getZoneClass()) && loc.getZ() == 4));
    }
}
