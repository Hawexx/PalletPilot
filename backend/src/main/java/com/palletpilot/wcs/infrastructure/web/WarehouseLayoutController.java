package com.palletpilot.wcs.infrastructure.web;

import com.palletpilot.wcs.application.service.WarehouseLayoutService;
import com.palletpilot.wcs.domain.model.WarehouseLayout;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Warehouse Layout")
@RequestMapping("/api/layout")
@RequiredArgsConstructor
public class WarehouseLayoutController {

    private final WarehouseLayoutService service;

    @GetMapping
    @Operation(summary = "Get Warehouse Layout")
    public ResponseEntity<WarehouseLayout> getLayout() {
        return service.getLayout()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @PostMapping
    @Operation(summary = "Save Warehouse Layout")
    public ResponseEntity<WarehouseLayout> saveLayout(@RequestBody LayoutRequest request) {
        WarehouseLayout saved = service.saveLayout(request.layoutJson());
        return ResponseEntity.ok(saved);
    }

    public record LayoutRequest(String layoutJson) {
    }
}
