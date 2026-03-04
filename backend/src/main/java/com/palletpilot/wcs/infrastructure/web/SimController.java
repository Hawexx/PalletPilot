package com.palletpilot.wcs.infrastructure.web;

import com.palletpilot.wcs.infrastructure.sim.SimulationHardwareClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Simulation control endpoints for testing without real hardware.
 */
@RestController
@RequestMapping("/api/sim")
@RequiredArgsConstructor
public class SimController {

    private final SimulationHardwareClient simulationClient;

    /** Inject an error for a specific equipment. */
    @PostMapping("/inject-error")
    public ResponseEntity<Map<String, Object>> injectError(
            @RequestParam UUID equipmentId,
            @RequestParam int errorCode) {
        simulationClient.injectError(equipmentId, errorCode);
        return ResponseEntity.ok(Map.of(
                "equipmentId", equipmentId,
                "errorCode", errorCode,
                "injected", true));
    }

    /** Clear all error injections. */
    @PostMapping("/clear-errors")
    public ResponseEntity<Map<String, Object>> clearErrors() {
        simulationClient.clearErrors();
        return ResponseEntity.ok(Map.of("cleared", true));
    }

    /** Set delay for a specific equipment. */
    @PostMapping("/set-delay")
    public ResponseEntity<Map<String, Object>> setDelay(
            @RequestParam UUID equipmentId,
            @RequestParam long delayMs) {
        simulationClient.setDelay(equipmentId, delayMs);
        return ResponseEntity.ok(Map.of("equipmentId", equipmentId, "delayMs", delayMs));
    }

    /** Set default simulation delay. */
    @PostMapping("/set-default-delay")
    public ResponseEntity<Map<String, Object>> setDefaultDelay(@RequestParam long delayMs) {
        simulationClient.setDefaultDelay(delayMs);
        return ResponseEntity.ok(Map.of("defaultDelayMs", delayMs));
    }
}
