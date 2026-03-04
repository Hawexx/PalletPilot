package com.palletpilot.wcs.infrastructure.web;

import com.palletpilot.wcs.application.service.EquipmentService;
import com.palletpilot.wcs.domain.model.Equipment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.palletpilot.wcs.domain.model.enums.EquipmentType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Equipment Management")
@RequestMapping("/api/equipment")
@RequiredArgsConstructor
@Slf4j
public class EquipmentController {

    private final EquipmentService equipmentService;

    @GetMapping("/types")
    @Operation(summary = "List Equipment Types", description = "Returns all available equipment types (MOVU, KUKA, BECKHOFF, etc.)")
    public ResponseEntity<List<EquipmentType>> getEquipmentTypes() {
        return ResponseEntity.ok(Arrays.asList(EquipmentType.values()));
    }

    @PostMapping("/{id}/test")
    @Operation(summary = "Test Equipment Connection", description = "Verifies connectivity to a specific equipment unit")
    public ResponseEntity<TestResult> testConnection(@PathVariable UUID id) {
        log.info("Testing connection for equipment: {}", id);
        boolean success = equipmentService.testConnection(id);
        return ResponseEntity
                .ok(new TestResult(success, success ? "Connection successful" : "Connection failed (Unreachable)"));
    }

    @GetMapping
    @Operation(summary = "List All Equipment", description = "Returns all registered equipment with current status")
    public ResponseEntity<Iterable<Equipment>> getAllEquipment() {
        return ResponseEntity.ok(equipmentService.getAllEquipment());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get Equipment by ID")
    public ResponseEntity<Equipment> getEquipment(@PathVariable UUID id) {
        return ResponseEntity.ok(equipmentService.getEquipment(id));
    }

    @PostMapping
    @Operation(summary = "Register New Equipment", description = "Creates a new equipment entry with initial IDLE status")
    public ResponseEntity<Equipment> createEquipment(@Valid @RequestBody EquipmentRequest request) {
        Equipment equipment = Equipment.builder()
                .name(request.name())
                .type(request.type())
                .ipAddress(request.ipAddress())
                .port(request.port())
                .protocol(request.protocol())
                .apiKey(request.apiKey())
                .configJson(request.configJson())
                .build();
        return ResponseEntity.ok(equipmentService.createEquipment(equipment));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update Equipment", description = "Updates configuration of an existing equipment unit")
    public ResponseEntity<Equipment> updateEquipment(@PathVariable UUID id,
            @Valid @RequestBody EquipmentRequest request) {
        Equipment equipment = Equipment.builder()
                .name(request.name())
                .type(request.type())
                .ipAddress(request.ipAddress())
                .port(request.port())
                .protocol(request.protocol())
                .apiKey(request.apiKey())
                .configJson(request.configJson())
                .build();
        return ResponseEntity.ok(equipmentService.updateEquipment(id, equipment));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete Equipment")
    public ResponseEntity<Void> deleteEquipment(@PathVariable UUID id) {
        equipmentService.deleteEquipment(id);
        return ResponseEntity.noContent().build();
    }

    // DTOs
    public record EquipmentRequest(
            String name,
            EquipmentType type,
            String ipAddress,
            Integer port,
            String protocol,
            String apiKey,
            String configJson) {
    }

    public record TestResult(boolean success, String message) {
    }
}
