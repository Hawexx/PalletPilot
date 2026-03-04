package com.palletpilot.wcs.infrastructure.web;

import com.palletpilot.wcs.application.service.EquipmentService;
import com.palletpilot.wcs.domain.model.Equipment;
import com.palletpilot.wcs.domain.model.enums.EquipmentType;
import com.palletpilot.wcs.infrastructure.web.EquipmentController.EquipmentRequest;
import com.palletpilot.wcs.infrastructure.web.EquipmentController.TestResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EquipmentController.
 * Validates CRUD delegation, connection test, and equipment type listing.
 */
@ExtendWith(MockitoExtension.class)
class EquipmentControllerTest {

    @Mock
    private EquipmentService equipmentService;

    @InjectMocks
    private EquipmentController controller;

    // ── GET /types ──

    @Test
    @DisplayName("getEquipmentTypes → returns all EquipmentType enum values")
    void getEquipmentTypes_returnsAllValues() {
        ResponseEntity<List<EquipmentType>> response = controller.getEquipmentTypes();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(EquipmentType.values());
    }

    // ── POST /{id}/test ──

    @Test
    @DisplayName("testConnection → returns success=true when reachable")
    void testConnection_success() {
        UUID id = UUID.randomUUID();
        when(equipmentService.testConnection(id)).thenReturn(true);

        ResponseEntity<TestResult> response = controller.testConnection(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().message()).contains("successful");
    }

    @Test
    @DisplayName("testConnection → returns success=false when unreachable")
    void testConnection_failure() {
        UUID id = UUID.randomUUID();
        when(equipmentService.testConnection(id)).thenReturn(false);

        ResponseEntity<TestResult> response = controller.testConnection(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().message()).contains("failed");
    }

    // ── GET / ──

    @Test
    @DisplayName("getAllEquipment → delegates to service and returns 200")
    void getAllEquipment_returns200() {
        Equipment eq = Equipment.builder().id(UUID.randomUUID()).name("KUKA-1").build();
        when(equipmentService.getAllEquipment()).thenReturn(List.of(eq));

        ResponseEntity<Iterable<Equipment>> response = controller.getAllEquipment();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }

    // ── DELETE /{id} ──

    @Test
    @DisplayName("deleteEquipment → returns 204 No Content")
    void deleteEquipment_returns204() {
        UUID id = UUID.randomUUID();

        ResponseEntity<Void> response = controller.deleteEquipment(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(equipmentService).deleteEquipment(id);
    }

    // ── POST / (create) ──

    @Test
    @DisplayName("createEquipment → builds Equipment from request and delegates to service")
    void createEquipment_delegatesToService() {
        EquipmentRequest request = new EquipmentRequest(
                "Conveyor-1", EquipmentType.CONVEYOR, "192.168.1.100",
                502, "MODBUS_TCP", null, null);

        Equipment saved = Equipment.builder()
                .id(UUID.randomUUID()).name("Conveyor-1").type(EquipmentType.CONVEYOR).build();
        when(equipmentService.createEquipment(any())).thenReturn(saved);

        ResponseEntity<Equipment> response = controller.createEquipment(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getName()).isEqualTo("Conveyor-1");
        verify(equipmentService).createEquipment(any(Equipment.class));
    }
}
