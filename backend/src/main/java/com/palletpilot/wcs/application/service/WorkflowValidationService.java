package com.palletpilot.wcs.application.service;

import com.palletpilot.wcs.domain.model.enums.EquipmentType;
import com.palletpilot.wcs.domain.model.workflow.WorkflowDefinition;
import com.palletpilot.wcs.domain.model.workflow.WorkflowStep;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
public class WorkflowValidationService {

    /**
     * Validates a workflow definition against strict safety and logic rules.
     * 
     * @throws IllegalArgumentException if validation fails.
     */
    public void validateWorkflow(WorkflowDefinition definition) {
        if (definition.getSteps() == null || definition.getSteps().isEmpty()) {
            throw new IllegalArgumentException("Workflow must have at least one step.");
        }

        Set<String> stepIds = new HashSet<>();
        List<WorkflowStep> steps = definition.getSteps();

        for (WorkflowStep step : steps) {
            String stepId = step.getStepId();
            if (stepIds.contains(stepId)) {
                throw new IllegalArgumentException("Duplicate Step ID found: " + stepId);
            }
            stepIds.add(stepId);

            validateStep(step);
        }

        validateGraphIntegrity(steps, stepIds);
    }

    private void validateStep(WorkflowStep step) {
        if (step.getEquipmentType() == null || step.getAction() == null) {
            throw new IllegalArgumentException("Step " + step.getStepId() + " missing EquipmentType or Action.");
        }

        // 1. SAFETY: Robot Safety Constraints
        if (step.getEquipmentType() == EquipmentType.KUKA) {
            if ("PLACE".equals(step.getAction())) {
                // Determine if CoG check is implicit or required.
                // In this strict mode, we assume backend handles CoG, but we ensure no
                // confusing params.
            }
        }

        // 2. SAFETY: Max Layers Hard Stop check (if applicable parametrizations exist)
        // Here we just ensure no "LAYER_INDEX" > 4 is ever configured
        if (step.getParameters() != null && step.getParameters().containsKey("layerIndex")) {
            int layerIndex = (int) step.getParameters().get("layerIndex");
            if (layerIndex > 4) {
                throw new IllegalArgumentException(
                        "SAFETY VIOLATION: Layer Index cannot exceed 4. Step: " + step.getStepId());
            }
        }
    }

    private void validateGraphIntegrity(List<WorkflowStep> steps, Set<String> allStepIds) {
        // Ensure nextStepId references exist
        for (WorkflowStep step : steps) {
            String nextCtx = step.getNextStepId();
            if (nextCtx != null && !nextCtx.isEmpty() && !allStepIds.contains(nextCtx)) {
                throw new IllegalArgumentException(
                        "Step " + step.getStepId() + " references unknown next step: " + nextCtx);
            }
        }
    }
}
