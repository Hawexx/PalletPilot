package com.palletpilot.wcs.infrastructure.web;

import com.palletpilot.wcs.application.service.JourneyOrchestrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * REST API for orchestrating pallet journeys.
 */
@RestController
@RequestMapping("/api/orchestration")
@RequiredArgsConstructor
public class OrchestrationController {

    private final JourneyOrchestrationService orchestrationService;

    /** Start a new pallet journey. */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startJourney(
            @RequestParam UUID palletId,
            @RequestParam(defaultValue = "DEFAULT") String journeyType) {
        UUID journeyId = orchestrationService.startJourney(palletId, journeyType);
        return ResponseEntity.ok(Map.of(
                "journeyId", journeyId,
                "status", "STARTED"));
    }

    /** Advance journey to the next step. */
    @PostMapping("/{journeyId}/advance")
    public ResponseEntity<Map<String, Object>> advanceJourney(@PathVariable UUID journeyId) {
        boolean advanced = orchestrationService.advanceJourney(journeyId);
        return ResponseEntity.ok(Map.of(
                "journeyId", journeyId,
                "advanced", advanced));
    }

    /** Get journey status with all steps. */
    @GetMapping("/{journeyId}/status")
    public ResponseEntity<Map<String, Object>> getJourneyStatus(@PathVariable UUID journeyId) {
        return ResponseEntity.ok(orchestrationService.getJourneyStatus(journeyId));
    }

    /**
     * Handle equipment completion callback (called internally or from PLC webhook).
     */
    @PostMapping("/callback/completion")
    public ResponseEntity<Void> handleCompletion(
            @RequestParam String commandId,
            @RequestParam(defaultValue = "true") boolean success,
            @RequestParam(required = false) String errorDetails) {
        orchestrationService.handleStepCompletion(commandId, success, errorDetails);
        return ResponseEntity.ok().build();
    }
}
