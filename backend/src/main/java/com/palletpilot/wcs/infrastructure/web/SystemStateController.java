package com.palletpilot.wcs.infrastructure.web;

import com.palletpilot.wcs.application.service.SystemStateService;
import com.palletpilot.wcs.domain.model.enums.SystemState;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/system/state")
@RequiredArgsConstructor
@Tag(name = "System State", description = "Global System State & E-Stop")
public class SystemStateController {

    private final SystemStateService systemStateService;

    @GetMapping
    @Operation(summary = "Get Current System State")
    public ResponseEntity<Map<String, Object>> getState() {
        return ResponseEntity.ok(Map.of(
                "state", systemStateService.getCurrentState(),
                "isRunning", systemStateService.isRunning(),
                "isEStop", systemStateService.isEmergencyStop(),
                "lastTransitionTime", systemStateService.getLastTransitionTime(),
                "lastTriggeredBy", systemStateService.getLastTriggeredBy()));
    }

    @PostMapping
    @Operation(summary = "Change System State (e.g., E-Stop)")
    public ResponseEntity<String> changeState(@RequestParam SystemState newState,
            @RequestParam(defaultValue = "Unknown") String userId,
            @RequestParam(defaultValue = "Manual Override") String reason) {
        try {
            systemStateService.transitionTo(newState, userId, reason);
            return ResponseEntity.ok("State transitioned to " + newState);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
