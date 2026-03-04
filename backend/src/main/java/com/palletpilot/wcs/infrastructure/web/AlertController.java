package com.palletpilot.wcs.infrastructure.web;

import com.palletpilot.wcs.application.service.AlertService;
import com.palletpilot.wcs.domain.model.Alert;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.UUID;

@RestController
@Tag(name = "Alerts")
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    @GetMapping
    @Operation(summary = "List All Alerts")
    public ResponseEntity<Iterable<Alert>> getAllAlerts() {
        return ResponseEntity.ok(alertService.getAllAlerts());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete Alert")
    public ResponseEntity<Void> deleteAlert(@PathVariable UUID id) {
        alertService.deleteAlert(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    @Operation(summary = "Clear All Alerts")
    public ResponseEntity<Void> deleteAllAlerts() {
        alertService.deleteAllAlerts();
        return ResponseEntity.noContent().build();
    }
}
