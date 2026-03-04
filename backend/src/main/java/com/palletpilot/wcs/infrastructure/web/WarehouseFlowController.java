package com.palletpilot.wcs.infrastructure.web;

import com.palletpilot.wcs.domain.model.WarehouseLayout;
import com.palletpilot.wcs.infrastructure.persistence.repository.WarehouseLayoutRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Warehouse Flow Control")
@RequestMapping("/api/warehouse-flow")
@RequiredArgsConstructor
@Slf4j
public class WarehouseFlowController {

    private final WarehouseLayoutRepository repository;

    @GetMapping
    @Operation(summary = "Get Warehouse Flow")
    public ResponseEntity<WarehouseLayout> getFlow() {
        return ResponseEntity.ok(
                repository.findById("DEFAULT")
                        .orElse(WarehouseLayout.builder().id("DEFAULT").layoutJson("[]").build()));
    }

    @PutMapping
    @Operation(summary = "Save Warehouse Flow")
    public ResponseEntity<WarehouseLayout> saveFlow(@RequestBody String layoutJson) {
        log.info("Saving warehouse flow");
        WarehouseLayout layout = WarehouseLayout.builder()
                .id("DEFAULT") // Singleton
                .layoutJson(layoutJson)
                .build();
        return ResponseEntity.ok(repository.save(layout));
    }
}
