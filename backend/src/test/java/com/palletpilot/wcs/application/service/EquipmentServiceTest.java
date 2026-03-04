package com.palletpilot.wcs.application.service;

import com.palletpilot.wcs.domain.model.Equipment;
import com.palletpilot.wcs.domain.model.enums.EquipmentState;
import com.palletpilot.wcs.domain.model.enums.EquipmentType;
import com.palletpilot.wcs.domain.port.out.HardwareClient;
import com.palletpilot.wcs.infrastructure.license.LicenseGuard;
import com.palletpilot.wcs.infrastructure.persistence.repository.CommandLogRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.EquipmentRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link EquipmentService}.
 * Covers CRUD operations, command execution safety checks, and duplicate
 * handling.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EquipmentServiceTest {

    @Mock
    private EquipmentRepository equipmentRepository;
    @Mock
    private AlertService alertService;
    @Mock
    private ConfigurationService configService;
    @Mock
    private SystemStateService systemStateService;
    @Mock
    private CommandLogRepository commandLogRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private HardwareClient simulationClient;

    @InjectMocks
    private EquipmentService equipmentService;

    private UUID testEquipmentId;
    private Equipment testEquipment;

    @BeforeAll
    static void initLicense() {
        LicenseGuard.earlyInstall();
    }

    @BeforeEach
    void setUp() {
        testEquipmentId = UUID.randomUUID();
        testEquipment = Equipment.builder()
                .id(testEquipmentId)
                .name("Conveyor-A1")
                .type(EquipmentType.CONVEYOR)
                .status(EquipmentState.IDLE)
                .build();

        // Inject the simulation client via the clients map
        try {
            var field = EquipmentService.class.getDeclaredField("clients");
            field.setAccessible(true);
            field.set(equipmentService, Map.of("simulationClient", simulationClient));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ── CRUD Tests ──────────────────────────────────────────────

    @Test
    @DisplayName("getEquipment — should return equipment by ID")
    void testGetEquipment_success() {
        when(equipmentRepository.findById(testEquipmentId)).thenReturn(Optional.of(testEquipment));

        Equipment result = equipmentService.getEquipment(testEquipmentId);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Conveyor-A1");
    }

    @Test
    @DisplayName("getEquipment — should throw when not found")
    void testGetEquipment_notFound() {
        when(equipmentRepository.findById(testEquipmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> equipmentService.getEquipment(testEquipmentId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("createEquipment — should save and set IDLE status")
    void testCreateEquipment_success() {
        when(equipmentRepository.existsByName("Conveyor-A1")).thenReturn(false);
        when(equipmentRepository.save(any(Equipment.class))).thenReturn(testEquipment);

        Equipment result = equipmentService.createEquipment(testEquipment);

        assertThat(result).isNotNull();
        verify(equipmentRepository).save(testEquipment);
        assertThat(testEquipment.getStatus()).isEqualTo(EquipmentState.IDLE);
    }

    @Test
    @DisplayName("createEquipment — should reject duplicate name")
    void testCreateEquipment_duplicateName() {
        when(equipmentRepository.existsByName("Conveyor-A1")).thenReturn(true);

        assertThatThrownBy(() -> equipmentService.createEquipment(testEquipment))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    // ── Command Execution Tests ─────────────────────────────────

    @Test
    @DisplayName("executeCommand — should reject when system not RUNNING")
    void testExecuteCommand_systemNotRunning() {
        when(systemStateService.isRunning()).thenReturn(false);
        when(systemStateService.getCurrentState()).thenReturn(
                com.palletpilot.wcs.domain.model.enums.SystemState.STOPPED);

        assertThatThrownBy(() -> equipmentService.executeCommand(testEquipmentId, "MOVE_TO_POS_1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SAFETY BLOCK");
    }

    @Test
    @DisplayName("executeCommand — should reject when equipment OFFLINE")
    void testExecuteCommand_equipmentOffline() {
        testEquipment.setStatus(EquipmentState.OFFLINE);
        when(systemStateService.isRunning()).thenReturn(true);
        when(equipmentRepository.findById(testEquipmentId)).thenReturn(Optional.of(testEquipment));

        assertThatThrownBy(() -> equipmentService.executeCommand(testEquipmentId, "MOVE_TO_POS_1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OFFLINE");
    }

    @Test
    @DisplayName("executeCommand — should succeed for IDLE equipment in RUNNING system")
    void testExecuteCommand_success() {
        when(systemStateService.isRunning()).thenReturn(true);
        when(equipmentRepository.findById(testEquipmentId)).thenReturn(Optional.of(testEquipment));
        when(configService.getBoolean("app.simulation.enabled", false)).thenReturn(true);
        when(commandLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(simulationClient.sendCommand(any(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(true));

        equipmentService.executeCommand(testEquipmentId, "MOVE_TO_POS_1");

        assertThat(testEquipment.getStatus()).isEqualTo(EquipmentState.BUSY);
        verify(simulationClient).sendCommand(eq(testEquipmentId), eq("MOVE_TO_POS_1"));
    }
}
