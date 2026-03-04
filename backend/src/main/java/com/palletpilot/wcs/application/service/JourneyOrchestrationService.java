package com.palletpilot.wcs.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.palletpilot.wcs.domain.model.JourneyStep;
import com.palletpilot.wcs.domain.model.enums.ProcessState;
import com.palletpilot.wcs.domain.model.workflow.WorkflowInstance;
import com.palletpilot.wcs.infrastructure.persistence.repository.JourneyStepRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.WorkflowInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Orchestration engine for pallet journeys.
 *
 * A journey moves a pallet through a sequence of steps:
 * KUKA_PICK → CONVEYOR_START → WRAP_START → CONVEYOR_STOP → MOVU_MISSION_CREATE
 *
 * Features:
 * - Persisted per-step state for resume-on-restart
 * - Compensation/rollback on failure
 * - Safety: blocks in EMERGENCY_STOP
 * - Concurrency control: one active journey per pallet
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JourneyOrchestrationService {

    private final WorkflowInstanceRepository workflowInstanceRepository;
    private final JourneyStepRepository journeyStepRepository;
    private final EquipmentService equipmentService;
    private final SystemStateService systemStateService;
    private final AlertService alertService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    // Default pallet journey template
    private static final List<JourneyStepTemplate> DEFAULT_JOURNEY = List.of(
            new JourneyStepTemplate("KUKA_PICK", "KUKA_HOME"),
            new JourneyStepTemplate("CONVEYOR_START", null),
            new JourneyStepTemplate("WRAP_START", "CONVEYOR_STOP"),
            new JourneyStepTemplate("CONVEYOR_STOP", null),
            new JourneyStepTemplate("MOVU_MISSION_CREATE", null));

    /**
     * Start a new pallet journey orchestration.
     *
     * @param palletId    Target pallet UUID
     * @param journeyType Type of journey (e.g., "INBOUND_TO_STORAGE")
     * @return Journey instance ID
     */
    @Transactional
    public UUID startJourney(UUID palletId, String journeyType) {
        // Safety check
        if (systemStateService.isEmergencyStop()) {
            throw new IllegalStateException("Cannot start journey: system in EMERGENCY_STOP");
        }
        if (!systemStateService.isRunning()) {
            throw new IllegalStateException("Cannot start journey: system is " + systemStateService.getCurrentState());
        }

        // Check for existing active journey on this pallet
        List<WorkflowInstance> active = workflowInstanceRepository.findByPalletIdAndStatus(palletId,
                ProcessState.CREATED);
        if (!active.isEmpty()) {
            throw new IllegalStateException("Pallet " + palletId + " already has an active journey");
        }

        // Create workflow instance
        WorkflowInstance journey = WorkflowInstance.builder()
                .definition(null) // Using default template, no definition FK needed initially
                .palletId(palletId)
                .journeyType(journeyType != null ? journeyType : "DEFAULT")
                .status(ProcessState.CREATED)
                .build();
        journey = workflowInstanceRepository.save(journey);

        // Create steps from template
        List<JourneyStepTemplate> template = DEFAULT_JOURNEY;
        for (int i = 0; i < template.size(); i++) {
            JourneyStepTemplate tmpl = template.get(i);
            JourneyStep step = JourneyStep.builder()
                    .journeyId(journey.getId())
                    .stepIndex(i)
                    .commandType(tmpl.commandType)
                    .compensationAction(tmpl.compensationAction)
                    .status("PENDING")
                    .build();
            journeyStepRepository.save(step);
        }

        auditService.log("JOURNEY_STARTED", "Journey", journey.getId().toString(), null,
                String.format("{\"palletId\":\"%s\",\"journeyType\":\"%s\",\"steps\":%d}",
                        palletId, journeyType, template.size()));

        log.info("[ORCHESTRATION] Journey {} started for pallet {} ({} steps)",
                journey.getId(), palletId, template.size());

        return journey.getId();
    }

    /**
     * Advance the journey by executing the next pending step.
     * Called after a step completes, or to resume after restart.
     *
     * @return true if a step was executed, false if journey is complete or blocked
     */
    @Transactional
    public boolean advanceJourney(UUID journeyId) {
        // Safety check
        if (systemStateService.isEmergencyStop()) {
            log.warn("[ORCHESTRATION] Journey {} blocked: EMERGENCY_STOP", journeyId);
            return false;
        }

        WorkflowInstance journey = workflowInstanceRepository.findById(journeyId)
                .orElseThrow(() -> new IllegalArgumentException("Journey not found: " + journeyId));

        if (journey.getStatus() != ProcessState.CREATED) {
            log.info("[ORCHESTRATION] Journey {} is {}, not advancing",
                    journeyId, journey.getStatus());
            return false;
        }

        List<JourneyStep> steps = journeyStepRepository.findByJourneyIdOrderByStepIndex(journeyId);

        // Find first non-terminal step
        JourneyStep nextStep = null;
        for (JourneyStep step : steps) {
            if (step.isPending()) {
                nextStep = step;
                break;
            }
            if (step.isRunning()) {
                log.info("[ORCHESTRATION] Journey {} waiting on step {} ({})",
                        journeyId, step.getStepIndex(), step.getCommandType());
                return false; // Still waiting
            }
            if (step.isFailed()) {
                log.warn("[ORCHESTRATION] Journey {} step {} failed, triggering compensation",
                        journeyId, step.getStepIndex());
                compensateJourney(journeyId, step.getStepIndex());
                return false;
            }
        }

        if (nextStep == null) {
            // All steps completed
            journey.setStatus(ProcessState.COMPLETED);
            workflowInstanceRepository.save(journey);
            log.info("[ORCHESTRATION] Journey {} COMPLETED", journeyId);
            auditService.log("JOURNEY_COMPLETED", "Journey", journeyId.toString(), null, "{}");
            return false;
        }

        // Execute the step
        return executeStep(journey, nextStep);
    }

    /**
     * Execute a single journey step by dispatching a command.
     */
    private boolean executeStep(WorkflowInstance journey, JourneyStep step) {
        try {
            log.info("[ORCHESTRATION] Executing step {} ({}) for journey {}",
                    step.getStepIndex(), step.getCommandType(), journey.getId());

            // Build command payload
            String commandPayload = buildCommandPayload(step);

            // Find equipment for this step type
            UUID equipmentId = step.getEquipmentId();
            if (equipmentId == null) {
                log.warn("[ORCHESTRATION] No equipment assigned for step {}, skipping",
                        step.getStepIndex());
                step.setStatus("SKIPPED");
                journeyStepRepository.save(step);
                return true;
            }

            // Dispatch command via EquipmentService
            String commandId = equipmentService.executeCommand(equipmentId, commandPayload);

            step.markRunning(commandId);
            journeyStepRepository.save(step);

            journey.setCurrentStepId(step.getId().toString());
            journey.setLastCommandId(commandId);
            workflowInstanceRepository.save(journey);

            return true;

        } catch (Exception e) {
            log.error("[ORCHESTRATION] Step {} failed: {}", step.getStepIndex(), e.getMessage());
            step.markFailed(e.getMessage());
            journeyStepRepository.save(step);
            return false;
        }
    }

    /**
     * Handle command completion callback.
     * Called by EquipmentService when a command finishes.
     */
    @Transactional
    public void handleStepCompletion(String commandId, boolean success, String errorDetails) {
        List<JourneyStep> steps = journeyStepRepository.findByCommandId(commandId);
        if (steps.isEmpty()) {
            return; // Not a journey command
        }

        JourneyStep step = steps.get(0);
        if (success) {
            step.markCompleted();
            log.info("[ORCHESTRATION] Step {} COMPLETED for journey {}",
                    step.getStepIndex(), step.getJourneyId());
        } else {
            step.markFailed(errorDetails);
            log.error("[ORCHESTRATION] Step {} FAILED for journey {}: {}",
                    step.getStepIndex(), step.getJourneyId(), errorDetails);
        }
        journeyStepRepository.save(step);

        // Auto-advance to next step
        advanceJourney(step.getJourneyId());
    }

    /**
     * Compensate (rollback) a journey from a failed step backwards.
     * Executes compensation actions for completed steps in reverse order.
     */
    @Transactional
    public void compensateJourney(UUID journeyId, int fromStepIndex) {
        log.warn("[ORCHESTRATION] Compensating journey {} from step {}", journeyId, fromStepIndex);

        List<JourneyStep> steps = journeyStepRepository.findByJourneyIdOrderByStepIndex(journeyId);

        // Walk backwards from the failed step
        for (int i = fromStepIndex - 1; i >= 0; i--) {
            JourneyStep step = steps.get(i);
            if (step.isCompleted() && step.getCompensationAction() != null) {
                try {
                    log.info("[ORCHESTRATION] Compensating step {} with {}",
                            step.getStepIndex(), step.getCompensationAction());

                    if (step.getEquipmentId() != null) {
                        equipmentService.executeCommand(step.getEquipmentId(), step.getCompensationAction());
                    }
                    step.markCompensated();
                    journeyStepRepository.save(step);

                } catch (Exception e) {
                    log.error("[ORCHESTRATION] Compensation failed for step {}: {}",
                            step.getStepIndex(), e.getMessage());
                    // Continue compensating other steps
                }
            }
        }

        // Mark journey as FAILED
        workflowInstanceRepository.findById(journeyId).ifPresent(journey -> {
            journey.setStatus(ProcessState.ERROR);
            journey.setErrorDetails("Compensation completed after step " + fromStepIndex + " failure");
            workflowInstanceRepository.save(journey);
        });

        alertService.raiseAlert(null,
                String.format("Journey %s failed and compensated from step %d", journeyId, fromStepIndex),
                "WARNING");

        auditService.log("JOURNEY_COMPENSATED", "Journey", journeyId.toString(), null,
                String.format("{\"fromStep\":%d}", fromStepIndex));
    }

    /**
     * Get journey status with all steps.
     */
    public Map<String, Object> getJourneyStatus(UUID journeyId) {
        WorkflowInstance journey = workflowInstanceRepository.findById(journeyId)
                .orElseThrow(() -> new IllegalArgumentException("Journey not found"));

        List<JourneyStep> steps = journeyStepRepository.findByJourneyIdOrderByStepIndex(journeyId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("journeyId", journeyId);
        result.put("palletId", journey.getPalletId());
        result.put("journeyType", journey.getJourneyType());
        result.put("status", journey.getStatus().name());
        result.put("currentStep", journey.getCurrentStepId());
        result.put("errorDetails", journey.getErrorDetails());

        List<Map<String, Object>> stepList = new ArrayList<>();
        for (JourneyStep step : steps) {
            Map<String, Object> stepMap = new LinkedHashMap<>();
            stepMap.put("index", step.getStepIndex());
            stepMap.put("commandType", step.getCommandType());
            stepMap.put("status", step.getStatus());
            stepMap.put("commandId", step.getCommandId());
            stepMap.put("startedAt", step.getStartedAt());
            stepMap.put("completedAt", step.getCompletedAt());
            stepMap.put("errorDetails", step.getErrorDetails());
            stepList.add(stepMap);
        }
        result.put("steps", stepList);
        return result;
    }

    private String buildCommandPayload(JourneyStep step) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("commandType", step.getCommandType());
        payload.put("stepIndex", step.getStepIndex());
        payload.put("journeyId", step.getJourneyId().toString());

        if (step.getParameters() != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> params = objectMapper.readValue(step.getParameters(), Map.class);
                payload.putAll(params);
            } catch (Exception e) {
                log.warn("[ORCHESTRATION] Failed to parse step parameters: {}", e.getMessage());
            }
        }

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return "{\"commandType\":\"" + step.getCommandType() + "\"}";
        }
    }

    // --- Inner types ---

    private record JourneyStepTemplate(String commandType, String compensationAction) {
    }
}
