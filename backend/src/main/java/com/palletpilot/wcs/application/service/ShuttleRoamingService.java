package com.palletpilot.wcs.application.service;

import com.palletpilot.wcs.domain.model.MovuTask;
import com.palletpilot.wcs.domain.model.MovuTask.TaskState;
import com.palletpilot.wcs.domain.model.MovuTask.TaskType;
import com.palletpilot.wcs.infrastructure.movu.MovuClient;
import com.palletpilot.wcs.infrastructure.movu.exception.MovuExceptions.MovuUnavailableException;
import com.palletpilot.wcs.infrastructure.persistence.repository.MovuTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates shuttle roaming between levels via dock/undock tasks.
 *
 * Full roaming flow:
 * 1. POST /tasks/dock → shuttle docks onto lift
 * 2. PLC: move lift to target level (simulation: log only)
 * 3. POST /tasks/undock → shuttle undocks from lift
 * 4. TaskStateChanged webhooks update MovuTask state
 *
 * This is a critical physical operation — dock/undock are NOT retried
 * automatically (physical side effects).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ShuttleRoamingService {

    private final MovuClient movuClient;
    private final MovuTaskRepository taskRepository;
    private final AlertService alertService;

    private static final int TASK_TIMEOUT_SECONDS = 60;

    /**
     * Initiate shuttle roaming from sourceLevel to targetLevel.
     * Creates task records and sends dock command to aSTC.
     */
    @Transactional
    public MovuTask initiateRoaming(UUID equipmentId, int vehicleId,
            int sourceLevel, int targetLevel, Integer liftSection) {
        String cid = MDC.get("correlationId");
        log.info("[{}] Initiating roaming: vehicle={}, level {} → {}, lift={}",
                cid, vehicleId, sourceLevel, targetLevel, liftSection);

        // Check for existing active tasks for this vehicle
        var activeTasks = taskRepository.findByEquipmentIdAndVehicleIdAndStateIn(
                equipmentId, vehicleId,
                List.of(TaskState.NOT_ASSIGNED, TaskState.ASSIGNED, TaskState.MOVING_SHUTTLE,
                        TaskState.WAIT_FOR_HANDOVER));
        if (!activeTasks.isEmpty()) {
            log.warn("[{}] Vehicle {} already has active tasks: {}", cid, vehicleId, activeTasks.size());
            throw new IllegalStateException("Vehicle " + vehicleId + " already has active roaming tasks");
        }

        // Create dock task
        MovuTask dockTask = MovuTask.builder()
                .equipmentId(equipmentId)
                .vehicleId(vehicleId)
                .taskType(TaskType.DOCK)
                .sourceLevel(sourceLevel)
                .targetLevel(targetLevel)
                .liftSection(liftSection)
                .build();
        taskRepository.save(dockTask);

        // Send dock command to aSTC — NOT retried
        try {
            Map<String, Object> dockPayload = Map.of(
                    "vehicleId", String.valueOf(vehicleId),
                    "section", liftSection != null ? liftSection : 1,
                    "level", sourceLevel);
            movuClient.dockShuttle(equipmentId, dockPayload);
            dockTask.setState(TaskState.ASSIGNED);
            taskRepository.save(dockTask);
            log.info("[{}] Dock task created and submitted for vehicle {}", cid, vehicleId);
        } catch (MovuUnavailableException e) {
            dockTask.setState(TaskState.FAILED);
            taskRepository.save(dockTask);
            log.error("[{}] Failed to dock: circuit breaker open", cid);
            throw e;
        } catch (Exception e) {
            dockTask.setState(TaskState.FAILED);
            taskRepository.save(dockTask);
            log.error("[{}] Failed to dock vehicle {}: {}", cid, vehicleId, e.getMessage());
            throw e;
        }

        return dockTask;
    }

    /**
     * Handle TaskStateChanged webhook for dock/undock tasks.
     */
    @Transactional
    public void handleTaskStateChanged(String taskId, String newState, UUID equipmentId) {
        var taskOpt = taskRepository.findByTaskId(taskId);
        if (taskOpt.isEmpty()) {
            log.debug("TaskStateChanged for unknown task {}, ignoring", taskId);
            return;
        }

        MovuTask task = taskOpt.get();
        TaskState mappedState = mapTaskState(newState);
        task.setState(mappedState);
        task.setLastEventAt(Instant.now());
        task.setUpdatedAt(Instant.now());
        taskRepository.save(task);

        log.info("Task {} ({}) state changed to {}", taskId, task.getTaskType(), mappedState);

        // If dock completed (DONE/WAIT_FOR_HANDOVER), initiate lift move
        if (task.getTaskType() == TaskType.DOCK &&
                (mappedState == TaskState.DONE || mappedState == TaskState.WAIT_FOR_HANDOVER)) {
            handleDockComplete(task);
        }
    }

    /**
     * After dock completes, simulate lift movement and initiate undock.
     * In production, this would integrate with PLC (PlcService.moveLiftToLevel).
     */
    private void handleDockComplete(MovuTask dockTask) {
        log.info("Dock complete for vehicle {}. Simulating lift move: level {} → {}",
                dockTask.getVehicleId(), dockTask.getSourceLevel(), dockTask.getTargetLevel());

        // SIMULATION: In production, this would call PlcService
        // plcService.moveLiftToLevel(dockTask.getLiftSection(),
        // dockTask.getTargetLevel());
        // For now, we immediately proceed to undock

        try {
            MovuTask undockTask = MovuTask.builder()
                    .equipmentId(dockTask.getEquipmentId())
                    .vehicleId(dockTask.getVehicleId())
                    .taskType(TaskType.UNDOCK)
                    .sourceLevel(dockTask.getSourceLevel())
                    .targetLevel(dockTask.getTargetLevel())
                    .liftSection(dockTask.getLiftSection())
                    .build();
            taskRepository.save(undockTask);

            Map<String, Object> undockPayload = Map.of(
                    "vehicleId", String.valueOf(dockTask.getVehicleId()),
                    "section", dockTask.getLiftSection() != null ? dockTask.getLiftSection() : 1,
                    "level", dockTask.getTargetLevel());
            movuClient.undockShuttle(dockTask.getEquipmentId(), undockPayload);
            undockTask.setState(TaskState.ASSIGNED);
            taskRepository.save(undockTask);
            log.info("Undock task submitted for vehicle {}", dockTask.getVehicleId());
        } catch (Exception e) {
            log.error("Failed to initiate undock for vehicle {}: {}",
                    dockTask.getVehicleId(), e.getMessage());
            alertService.raiseAlert(dockTask.getEquipmentId(),
                    "Shuttle roaming undock failed for vehicle " + dockTask.getVehicleId() +
                            ": " + e.getMessage(),
                    "CRITICAL");
        }
    }

    /**
     * Watchdog: check for stuck tasks.
     */
    @Scheduled(fixedDelay = 30000) // Every 30 seconds
    @Transactional
    public void watchStuckTasks() {
        Instant cutoff = Instant.now().minusSeconds(TASK_TIMEOUT_SECONDS);
        List<TaskState> activeStates = List.of(
                TaskState.ASSIGNED, TaskState.MOVING_SHUTTLE, TaskState.WAIT_FOR_HANDOVER);

        List<MovuTask> stuckTasks = taskRepository.findByStateInAndUpdatedAtBefore(activeStates, cutoff);

        for (MovuTask task : stuckTasks) {
            log.warn("Task {} ({}) stuck in state {} for >{} seconds",
                    task.getTaskId(), task.getTaskType(), task.getState(), TASK_TIMEOUT_SECONDS);
            task.setState(TaskState.TIMEOUT);
            task.setUpdatedAt(Instant.now());
            taskRepository.save(task);

            alertService.raiseAlert(task.getEquipmentId(),
                    "Roaming task " + task.getTaskType() + " for vehicle " + task.getVehicleId() +
                            " timed out in state " + task.getState(),
                    "CRITICAL");
        }
    }

    private TaskState mapTaskState(String astcState) {
        if (astcState == null)
            return TaskState.NOT_ASSIGNED;
        return switch (astcState.toLowerCase()) {
            case "notassigned" -> TaskState.NOT_ASSIGNED;
            case "assigned" -> TaskState.ASSIGNED;
            case "movingshuttle" -> TaskState.MOVING_SHUTTLE;
            case "waitforhandover" -> TaskState.WAIT_FOR_HANDOVER;
            case "done" -> TaskState.DONE;
            default -> TaskState.NOT_ASSIGNED;
        };
    }
}
