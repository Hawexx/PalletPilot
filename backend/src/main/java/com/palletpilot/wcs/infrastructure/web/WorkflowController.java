package com.palletpilot.wcs.infrastructure.web;

import com.palletpilot.wcs.application.service.WorkflowService;
import com.palletpilot.wcs.domain.model.workflow.WorkflowDefinition;
import com.palletpilot.wcs.domain.model.workflow.WorkflowInstance;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/workflows")
@RequiredArgsConstructor
@Tag(name = "Workflow Orchestration", description = "Manage equipment orchestration flows")
public class WorkflowController {

    private final WorkflowService workflowService;

    @PostMapping("/definitions")
    @Operation(summary = "Create a new Workflow Definition")
    public ResponseEntity<WorkflowDefinition> createDefinition(@RequestBody WorkflowDefinition definition) {
        return ResponseEntity.ok(workflowService.createDefinition(definition));
    }

    @PostMapping("/instances/start")
    @Operation(summary = "Start a workflow instance (Trigger)")
    public ResponseEntity<WorkflowInstance> startWorkflow(
            @RequestParam UUID definitionId,
            @RequestParam(required = false) UUID orderId) {
        return ResponseEntity.ok(workflowService.instantiateWorkflow(definitionId, orderId));
    }
}
