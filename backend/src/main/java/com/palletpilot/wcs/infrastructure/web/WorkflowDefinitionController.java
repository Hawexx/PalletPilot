package com.palletpilot.wcs.infrastructure.web;

import com.palletpilot.wcs.application.service.WorkflowService;
import com.palletpilot.wcs.domain.model.workflow.WorkflowDefinition;
import com.palletpilot.wcs.infrastructure.persistence.repository.WorkflowDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Workflow Definitions")
@RequestMapping("/api/workflow/definitions")
@RequiredArgsConstructor
@Slf4j
public class WorkflowDefinitionController {

    private final WorkflowService workflowService;
    private final WorkflowDefinitionRepository repository;

    @GetMapping
    @Operation(summary = "List Workflow Definitions")
    public List<WorkflowDefinition> getAll() {
        return repository.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get Workflow Definition by ID")
    public ResponseEntity<WorkflowDefinition> getById(@PathVariable UUID id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(summary = "Create/Update Workflow Definition")
    public ResponseEntity<WorkflowDefinition> createOrUpdate(@RequestBody WorkflowDefinition definition) {
        log.info("Saving Workflow Definition: {}", definition.getName());
        WorkflowDefinition saved = workflowService.createDefinition(definition);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete Workflow Definition")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
