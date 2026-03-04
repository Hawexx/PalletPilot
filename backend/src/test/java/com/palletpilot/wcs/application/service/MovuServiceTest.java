package com.palletpilot.wcs.application.service;

import com.palletpilot.wcs.domain.model.Equipment;
import com.palletpilot.wcs.domain.model.MovuLoadPosition;
import com.palletpilot.wcs.domain.model.MovuMission;
import com.palletpilot.wcs.domain.model.enums.EquipmentType;
import com.palletpilot.wcs.infrastructure.movu.MovuClient;
import com.palletpilot.wcs.infrastructure.persistence.repository.EquipmentRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.MovuLoadPositionRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.MovuMissionRepository;
import com.palletpilot.wcs.infrastructure.wms.WmsClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MovuService — mission orchestration layer.
 * Tests executeMission flow, load position memory, legacy callback, and
 * handoff.
 */
@ExtendWith(MockitoExtension.class)
class MovuServiceTest {

    @Mock
    private WmsClient wmsClient;
    @Mock
    private MovuClient movuClient;
    @Mock
    private PlcService plcService;
    @Mock
    private MovuMissionRepository missionRepository;
    @Mock
    private MovuLoadPositionRepository loadPositionRepository;
    @Mock
    private EquipmentRepository equipmentRepository;

    @InjectMocks
    private MovuService service;

    // ── executeMission ──

    @Test
    @DisplayName("executeMission: WMS returns null location → throws")
    void executeMission_wmsReturnsNull_throws() {
        when(wmsClient.getPalletLocation("SKU-X")).thenReturn(null);

        assertThatThrownBy(() -> service.executeMission("SKU-X"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SKU location not found");
    }

    @Test
    @DisplayName("executeMission: WMS returns empty → throws")
    void executeMission_wmsReturnsEmpty_throws() {
        when(wmsClient.getPalletLocation("SKU-Y")).thenReturn("");

        assertThatThrownBy(() -> service.executeMission("SKU-Y"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SKU location not found");
    }

    @Test
    @DisplayName("executeMission: happy path → creates mission with CREATED status")
    void executeMission_happyPath() {
        UUID eqId = UUID.randomUUID();
        Equipment movu = Equipment.builder().id(eqId).type(EquipmentType.MOVU).build();

        when(wmsClient.getPalletLocation("SKU-A")).thenReturn("LANE-3-L2-S1");
        when(equipmentRepository.findFirstByType(EquipmentType.MOVU)).thenReturn(Optional.of(movu));
        when(movuClient.createMission(eq(eqId), eq("PICK_DROP"), any())).thenReturn("MSN-100");
        when(loadPositionRepository.findById("SKU-A")).thenReturn(Optional.empty());

        String missionId = service.executeMission("SKU-A");

        assertThat(missionId).isEqualTo("MSN-100");
        verify(missionRepository).save(argThat(m -> ((MovuMission) m).getStatus().equals("CREATED")
                && ((MovuMission) m).getSku().equals("SKU-A")));
    }

    @Test
    @DisplayName("executeMission: load position exists → includes sourceDistance")
    void executeMission_withLoadPosition_includesDistance() {
        UUID eqId = UUID.randomUUID();
        Equipment movu = Equipment.builder().id(eqId).type(EquipmentType.MOVU).build();
        MovuLoadPosition pos = new MovuLoadPosition();
        pos.setDistanceMm(4500);
        pos.setLastMissionId("MSN-OLD");

        when(wmsClient.getPalletLocation("SKU-B")).thenReturn("LANE-1-L1-S1");
        when(equipmentRepository.findFirstByType(EquipmentType.MOVU)).thenReturn(Optional.of(movu));
        when(loadPositionRepository.findById("SKU-B")).thenReturn(Optional.of(pos));
        when(movuClient.createMission(eq(eqId), eq("PICK_DROP"),
                argThat(map -> map.containsKey("sourceDistance") && (int) map.get("sourceDistance") == 4500)))
                .thenReturn("MSN-POS");

        String missionId = service.executeMission("SKU-B");

        assertThat(missionId).isEqualTo("MSN-POS");
    }

    @Test
    @DisplayName("executeMission: no MOVU equipment configured → throws")
    void executeMission_noEquipment_throws() {
        when(wmsClient.getPalletLocation("SKU-C")).thenReturn("LANE-1");
        when(equipmentRepository.findFirstByType(EquipmentType.MOVU)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.executeMission("SKU-C"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No MOVU equipment configured");
    }

    // ── triggerHandoff ──

    @Test
    @DisplayName("triggerHandoff: conveyor found → starts at speed 50")
    void triggerHandoff_conveyorFound() {
        UUID convId = UUID.randomUUID();
        Equipment conv = Equipment.builder().id(convId).name("CONVEYOR-IN-1").build();
        when(equipmentRepository.findByName("CONVEYOR-IN-1")).thenReturn(Optional.of(conv));

        service.triggerHandoff("CONV-1");

        verify(plcService).startConveyor(convId, 50);
    }

    @Test
    @DisplayName("triggerHandoff: conveyor not found → throws")
    void triggerHandoff_conveyorMissing_throws() {
        when(equipmentRepository.findByName("CONVEYOR-IN-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.triggerHandoff("ANY"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Handoff Conveyor");
    }

    // ── legacy handleCallback ──

    @Test
    @DisplayName("handleCallback COMPLETED → triggers handoff")
    void handleCallback_completed_triggersHandoff() {
        MovuMission mission = MovuMission.builder().id("MSN-CB").targetLocation("CONV-1").build();
        Equipment conv = Equipment.builder().id(UUID.randomUUID()).name("CONVEYOR-IN-1").build();

        when(missionRepository.findById("MSN-CB")).thenReturn(Optional.of(mission));
        when(equipmentRepository.findByName("CONVEYOR-IN-1")).thenReturn(Optional.of(conv));

        service.handleCallback("MSN-CB", "COMPLETED");

        verify(missionRepository).save(argThat(m -> "COMPLETED".equals(((MovuMission) m).getStatus())));
        verify(plcService).startConveyor(any(), eq(50));
    }
}
