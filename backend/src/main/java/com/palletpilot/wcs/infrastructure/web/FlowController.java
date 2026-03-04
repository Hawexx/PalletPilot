package com.palletpilot.wcs.infrastructure.web;

import com.palletpilot.wcs.application.service.FlowExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.Map;

/**
 * REST Controller for executing warehouse flow commands.
 * 
 * Endpoints allow the frontend to trigger flow execution and control
 * individual equipment based on the warehouse flow layout.
 */
@RestController
@Tag(name = "Warehouse Flow")
@RequestMapping("/api/flow")
@RequiredArgsConstructor
@Slf4j
public class FlowController {

    private final FlowExecutionService flowExecutionService;

    /**
     * Get all conveyors configured in the flow layout
     */
    @GetMapping("/conveyors")
    @Operation(summary = "List Conveyors")
    public ResponseEntity<List<Map<String, Object>>> getConveyors() {
        return ResponseEntity.ok(flowExecutionService.getConveyors());
    }

    /**
     * Start a conveyor by its node ID
     */
    @PostMapping("/conveyors/{nodeId}/start")
    @Operation(summary = "Start Conveyor")
    public ResponseEntity<Map<String, Object>> startConveyor(
            @PathVariable String nodeId,
            @RequestBody(required = false) Map<String, String> body) {

        String direction = body != null ? body.get("direction") : null;
        String commandId = flowExecutionService.startConveyor(nodeId, direction);

        if (commandId != null) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "commandId", commandId,
                    "message", "Conveyor started"));
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Failed to start conveyor"));
        }
    }

    /**
     * Stop a conveyor by its node ID
     */
    @PostMapping("/conveyors/{nodeId}/stop")
    @Operation(summary = "Stop Conveyor")
    public ResponseEntity<Map<String, Object>> stopConveyor(@PathVariable String nodeId) {
        String commandId = flowExecutionService.stopConveyor(nodeId);

        if (commandId != null) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "commandId", commandId,
                    "message", "Conveyor stopped"));
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Failed to stop conveyor"));
        }
    }

    /**
     * Execute the full flow starting from inbound zone
     */
    @PostMapping("/execute/inbound")
    @Operation(summary = "Execute Inbound Flow")
    public ResponseEntity<Map<String, Object>> executeInboundFlow(
            @RequestBody Map<String, String> body) {

        String palletId = body.get("palletId");
        if (palletId == null || palletId.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "palletId is required"));
        }

        List<String> commandIds = flowExecutionService.executeFlowFromStart("ZONE", palletId);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "palletId", palletId,
                "commandIds", commandIds,
                "message", "Flow execution initiated",
                "stepsExecuted", commandIds.size()));
    }

    /**
     * Execute flow from a specific node type
     */
    @PostMapping("/execute/{nodeType}")
    @Operation(summary = "Execute Flow From Node")
    public ResponseEntity<Map<String, Object>> executeFlowFromNode(
            @PathVariable String nodeType,
            @RequestBody Map<String, String> body) {

        String palletId = body.get("palletId");
        if (palletId == null || palletId.isEmpty()) {
            palletId = "PALLET-" + System.currentTimeMillis();
        }

        List<String> commandIds = flowExecutionService.executeFlowFromStart(nodeType.toUpperCase(), palletId);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "palletId", palletId,
                "nodeType", nodeType,
                "commandIds", commandIds,
                "message", commandIds.isEmpty() ? "No nodes found for type " + nodeType : "Flow execution initiated"));
    }
}
