package com.palletpilot.wcs.application.service;

import com.palletpilot.wcs.domain.model.enums.ProcessState;
import com.palletpilot.wcs.domain.model.enums.EquipmentType;
import com.palletpilot.wcs.domain.model.workflow.WorkflowDefinition;
import com.palletpilot.wcs.domain.model.workflow.WorkflowInstance;
import com.palletpilot.wcs.domain.model.workflow.WorkflowStep;
import com.palletpilot.wcs.infrastructure.persistence.repository.WorkflowDefinitionRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.WorkflowInstanceRepository;
import com.palletpilot.wcs.domain.model.Equipment;
import com.palletpilot.wcs.infrastructure.persistence.repository.CommandLogRepository;
import com.palletpilot.wcs.domain.model.enums.CommandStatus;
import com.palletpilot.wcs.infrastructure.persistence.repository.EquipmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowService {

    private final WorkflowDefinitionRepository definitionRepository;
    private final WorkflowInstanceRepository instanceRepository;
    private final WorkflowValidationService validationService;
    private final EquipmentService equipmentService;
    private final EquipmentRepository equipmentRepository;
    private final SystemStateService systemStateService;
    private final CommandLogRepository commandLogRepository;

    /**
     * Creates a new reusable workflow template.
     */
    @Transactional
    public WorkflowDefinition createDefinition(WorkflowDefinition definition) {
        validationService.validateWorkflow(definition);
        return definitionRepository.save(definition);
    }

    /**
     * Instantiates a workflow for a specific Order.
     */
    @Transactional
    public WorkflowInstance instantiateWorkflow(UUID definitionId, UUID orderId) {
        WorkflowDefinition def = definitionRepository.findById(definitionId)
                .orElseThrow(() -> new IllegalArgumentException("Definition not found"));

        if (!def.isActive()) {
            throw new IllegalStateException("Workflow Definition is unused/inactive");
        }

        // Start at first step (naively assume first in list is start for now, or use
        // specific Start Node)
        // For MVP: The list order matters or we find one with no incoming edges.
        // Adhering to User Request: "Ordered sequence". We take index 0.
        String firstStepId = def.getSteps().get(0).getStepId();

        WorkflowInstance instance = WorkflowInstance.builder()
                .definition(def)
                .orderId(orderId)
                .status(ProcessState.PLANNED)
                .currentStepId(firstStepId)
                .build();

        return instanceRepository.save(instance);
    }

    /**
     * The Heartbeat of the Orchestrator.
     * Runs frequently to check active workflows and advance them if equipment is
     * ready.
     */
    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void processWorkflowTick() {
        // SAFETY CHECK
        if (!systemStateService.isRunning()) {
            // Log intermittently or just skip silently to avoid spam
            return;
        }

        List<WorkflowInstance> activeInstances = instanceRepository.findByStatus(ProcessState.RUNNING);

        // Also pick up PLANNED
        activeInstances.addAll(instanceRepository.findByStatus(ProcessState.PLANNED));

        for (WorkflowInstance instance : activeInstances) {
            try {
                processInstance(instance);
            } catch (Exception e) {
                log.error("Error processing workflow instance " + instance.getId(), e);
                instance.setStatus(ProcessState.FAILED);
                instance.setErrorDetails(e.getMessage());
                instanceRepository.save(instance);
            }
        }
    }

    private void processInstance(WorkflowInstance instance) {
        if (instance.getStatus() == ProcessState.PLANNED) {
            instance.setStatus(ProcessState.RUNNING);
            instanceRepository.save(instance);
        }

        // 1. Check Pending Command
        if (instance.getLastCommandId() != null) {
            checkPendingCommand(instance);
            return;
        }

        String currentStepId = instance.getCurrentStepId();
        if (currentStepId == null) {
            instance.setStatus(ProcessState.COMPLETED);
            instanceRepository.save(instance);
            return;
        }

        WorkflowDefinition def = instance.getDefinition();
        Optional<WorkflowStep> stepOpt = def.getSteps().stream()
                .filter(s -> s.getStepId().equals(currentStepId))
                .findFirst();

        if (stepOpt.isEmpty()) {
            throw new IllegalStateException("Current step ID " + currentStepId + " not found in definition");
        }

        WorkflowStep step = stepOpt.get();

        // 2. Find Equipment
        Equipment equipment = findEquipmentByType(step.getEquipmentType());
        if (equipment == null) {
            log.warn("No equipment found for type " + step.getEquipmentType() + ". Waiting...");
            return;
        }

        // 3. Check if Equipment is IDLE
        // We still check general status to avoid queuing if physically busy
        if (equipment.getStatus() != com.palletpilot.wcs.domain.model.enums.EquipmentState.IDLE) {
            return; // Wait
        }

        // 4. FIRE COMMAND
        String command = buildCommand(step);
        log.info("Workflow {} Step {}: Sending command '{}' to {}", instance.getId(), step.getStepId(), command,
                equipment.getName());

        // Send Async & Track ID
        String commandId = equipmentService.executeCommand(equipment.getId(), command);

        // 5. Update Instance State
        instance.setLastCommandId(commandId);
        instanceRepository.save(instance);
    }

    private void checkPendingCommand(WorkflowInstance instance) {
        String commandId = instance.getLastCommandId();
        Optional<com.palletpilot.wcs.domain.model.CommandLog> logOpt = commandLogRepository.findById(commandId);

        if (logOpt.isEmpty()) {
            log.error("Command ID {} missing. Failing workflow {}", commandId, instance.getId());
            instance.setStatus(ProcessState.FAILED);
            instance.setErrorDetails("Missing Command Log: " + commandId);
            instanceRepository.save(instance);
            return;
        }

        com.palletpilot.wcs.domain.model.CommandLog cmdLog = logOpt.get();

        if (cmdLog.getStatus() == CommandStatus.COMPLETED) {
            log.info("Command {} completed. Advancing workflow.", commandId);
            advanceStep(instance);
            instance.setLastCommandId(null);
            instanceRepository.save(instance);
        } else if (cmdLog.getStatus() == CommandStatus.FAILED || cmdLog.getStatus() == CommandStatus.TIMEOUT) {
            log.error("Command {} failed/timed out. Workflow Halting.", commandId);
            instance.setStatus(ProcessState.FAILED);
            instance.setErrorDetails("Command Failed: " + cmdLog.getFailureReason());
            instanceRepository.save(instance);
        } else {
            // Still SENT/ACKED. Check for local timeout manually if needed.
            if (cmdLog.getCreatedAt() != null
                    && java.time.Instant.now().minusSeconds(60).isAfter(cmdLog.getCreatedAt())) {
                cmdLog.setStatus(CommandStatus.TIMEOUT);
                commandLogRepository.save(cmdLog);
            }
        }
    }

    private void advanceStep(WorkflowInstance instance) {
        WorkflowDefinition def = instance.getDefinition();
        String currentId = instance.getCurrentStepId();
        Optional<WorkflowStep> step = def.getSteps().stream().filter(s -> s.getStepId().equals(currentId)).findFirst();

        if (step.isPresent()) {
            String nextId = step.get().getNextStepId();
            instance.setCurrentStepId(nextId);
            if (nextId == null) {
                instance.setStatus(ProcessState.COMPLETED);
            }
        }
    }

    private Equipment findEquipmentByType(EquipmentType type) {
        // Naive strategy: find first matching.
        // Real world: match by capabilities or specific Assignment in Workflow
        // Definition (parameters).
        return equipmentRepository.findFirstByType(type)
                .orElse(null);
    }

    private String buildCommand(WorkflowStep step) {
        // Map abstract Action + Params to concrete string command
        return step.getAction() + ":" + serializeParams(step.getParameters());
    }

    private String serializeParams(Map<String, Object> params) {
        if (params == null)
            return "";
        return params.toString(); // Naive JSON-ish
    }

    // External Trigger to advance step (called by Webhooks/Callbacks)
    @Transactional
    public void signalStepCompletion(UUID instanceId) {
        WorkflowInstance instance = instanceRepository.findById(instanceId).orElseThrow();
        WorkflowDefinition def = instance.getDefinition();

        String currentId = instance.getCurrentStepId();
        Optional<WorkflowStep> step = def.getSteps().stream().filter(s -> s.getStepId().equals(currentId)).findFirst();

        if (step.isPresent()) {
            String nextId = step.get().getNextStepId();
            instance.setCurrentStepId(nextId);
            if (nextId == null) {
                instance.setStatus(ProcessState.COMPLETED);
            }
            instanceRepository.save(instance);
            log.info("Workflow Instance {} advanced to Step {}", instanceId, nextId);
        }
    }
}
