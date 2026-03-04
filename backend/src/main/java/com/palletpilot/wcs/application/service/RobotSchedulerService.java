package com.palletpilot.wcs.application.service;

import com.palletpilot.wcs.domain.model.Equipment;
import com.palletpilot.wcs.domain.model.ChargingStation;
import com.palletpilot.wcs.domain.model.Robot;
import com.palletpilot.wcs.domain.model.TransportTask;
import com.palletpilot.wcs.domain.model.enums.RobotState;
import com.palletpilot.wcs.domain.model.enums.TaskStatus;
import com.palletpilot.wcs.infrastructure.persistence.repository.ChargingStationRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.EquipmentRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.RobotRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.TransportTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RobotSchedulerService {

    private final RobotRepository robotRepository;
    private final ChargingStationRepository stationRepository;
    private final TransportTaskRepository taskRepository;
    private final ConfigurationService configurationService;
    private final com.palletpilot.wcs.infrastructure.movu.MovuClient movuClient;
    private final com.palletpilot.wcs.infrastructure.persistence.repository.EquipmentRepository equipmentRepository;

    /**
     * Periodic check for robot health and task assignment.
     */
    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void scheduleRobots() {
        boolean simEnabled = configurationService.getBoolean("app.simulation.enabled", false);
        List<Robot> robots = robotRepository.findAll();

        for (Robot robot : robots) {
            if (simEnabled) {
                simulatePhysics(robot);
                checkBattery(robot); // Simulation logic
                if (robot.getState() == RobotState.IDLE) {
                    tryAssignTask(robot);
                }
            } else {
                // REAL HARDWARE LOGIC
                syncRealMovuState(robot);
                checkRealBattery(robot);
                // Real task assignment would go here (omitted to focus on Charging as
                // requested)
            }
        }
    }

    // --- REAL HARDWARE LOGIC ---

    private void syncRealMovuState(Robot robot) {
        // Resolve Equipment ID from Robot Name (Assumption: 1:1 mapping)
        Optional<Equipment> eqOpt = equipmentRepository.findByName(robot.getName());
        if (eqOpt.isEmpty()) {
            // log.warn("No Equipment found for Robot {}", robot.getName()); // Reduce spam
            return;
        }
        UUID equipmentId = eqOpt.get().getId();

        // Sync Battery
        Integer battery = movuClient.getBatteryLevel(equipmentId);
        if (battery != null) {
            robot.setBatteryLevel(battery);
        }
        // Could also sync Position/State here if MovuClient returns it
        robotRepository.save(robot);
    }

    private void checkRealBattery(Robot robot) {
        if (robot.getBatteryLevel() < 20 && robot.getState() != RobotState.CHARGING
                && robot.getState() != RobotState.MOVING) {
            log.warn("Real Robot {} Low Battery ({}%). Sending to Charger.", robot.getName(), robot.getBatteryLevel());
            sendToRealCharger(robot);
        }
    }

    private void sendToRealCharger(Robot robot) {
        Optional<ChargingStation> station = stationRepository.findByIsOccupiedFalse().stream()
                .min(Comparator.comparingInt(s -> distance(robot.getX(), robot.getY(), s.getX(), s.getY())));

        if (station.isPresent()) {
            ChargingStation s = station.get();
            log.info("Dispatching Real Robot {} to Charger {} (Lane: {}, Dist: {})",
                    robot.getName(), s.getName(), s.getX(), s.getY());

            // Reserve Station
            s.setOccupied(true);
            s.setOccupiedByRobotId(robot.getId());
            stationRepository.save(s);

            robot.setAssignedChargingStationId(s.getId());
            robot.setState(RobotState.MOVING);
            robotRepository.save(robot);

            // Resolve Equipment ID
            Optional<Equipment> eqOpt = equipmentRepository.findByName(robot.getName());
            if (eqOpt.isEmpty()) {
                log.error("Cannot charge Robot {}: No corresponding Equipment found", robot.getName());
                return;
            }
            UUID equipmentId = eqOpt.get().getId();

            // Send Command to Movu
            // Mapping: X -> Lane, Y -> Distance
            movuClient.sendToLocation(equipmentId, s.getX(), s.getY());

        } else {
            log.error("No chargers available for Real Robot {}!", robot.getName());
        }
    }

    // --- SIMULATION LOGIC ---

    private void simulatePhysics(Robot robot) {
        // Battery Drain
        if (robot.getState() == RobotState.IDLE) {
            if (Math.random() < 0.1)
                robot.setBatteryLevel(Math.max(0, robot.getBatteryLevel() - 1));
        } else if (robot.getState() == RobotState.MOVING || robot.getState() == RobotState.PICKING) {
            if (Math.random() < 0.3)
                robot.setBatteryLevel(Math.max(0, robot.getBatteryLevel() - 1));
        }

        // Movement Logic
        if (robot.getState() == RobotState.MOVING) {
            Integer targetX = robot.getX();
            Integer targetY = robot.getY();

            if (robot.getCurrentTaskId() != null) {
                Optional<TransportTask> taskOpt = taskRepository.findById(robot.getCurrentTaskId());
                if (taskOpt.isPresent()) {
                    TransportTask task = taskOpt.get();
                    if (task.getStatus() == TaskStatus.ASSIGNED) {
                        targetX = 0;
                        targetY = 0; // Source (Simulated)
                        if (distance(robot.getX(), robot.getY(), targetX, targetY) <= 1) {
                            robot.setX(targetX);
                            robot.setY(targetY);
                            task.setStatus(TaskStatus.IN_PROGRESS);
                            taskRepository.save(task);
                        } else {
                            moveTowards(robot, targetX, targetY);
                        }
                    } else if (task.getStatus() == TaskStatus.IN_PROGRESS) {
                        targetX = 20;
                        targetY = 20; // Target (Simulated)
                        if (distance(robot.getX(), robot.getY(), targetX, targetY) <= 1) {
                            robot.setX(targetX);
                            robot.setY(targetY);
                            task.setStatus(TaskStatus.COMPLETED);
                            taskRepository.save(task);
                            robot.setState(RobotState.IDLE);
                            robot.setCurrentTaskId(null);
                        } else {
                            moveTowards(robot, targetX, targetY);
                        }
                    }
                }
            } else if (robot.getAssignedChargingStationId() != null) {
                // Moving to Charger (Simulated Teleport logic was originally here)
                // We keep simulation separate.
            }
            robotRepository.save(robot);
        }
    }

    private void moveTowards(Robot robot, int tx, int ty) {
        int dx = Integer.compare(tx, robot.getX());
        int dy = Integer.compare(ty, robot.getY());
        robot.setX(robot.getX() + dx);
        robot.setY(robot.getY() + dy);
    }

    private void checkBattery(Robot robot) {
        if (robot.getState() == RobotState.CHARGING) {
            handleCharging(robot);
            return;
        }
        if (robot.getBatteryLevel() < 20) {
            log.warn("Robot {} battery CRITICAL ({}%). Routing to charger (Simulated).", robot.getName(),
                    robot.getBatteryLevel());
            if (robot.getState() != RobotState.MOVING) {
                sendToCharge(robot);
            }
        }
    }

    private void handleCharging(Robot robot) {
        if (robot.getBatteryLevel() >= 95) {
            log.info("Robot {} fully charged. Releasing.", robot.getName());
            robot.setState(RobotState.IDLE);
            if (robot.getAssignedChargingStationId() != null) {
                stationRepository.findById(robot.getAssignedChargingStationId()).ifPresent(s -> {
                    s.setOccupied(false);
                    s.setOccupiedByRobotId(null);
                    stationRepository.save(s);
                });
                robot.setAssignedChargingStationId(null);
            }
            robotRepository.save(robot);
        } else {
            robot.setBatteryLevel(Math.min(100, robot.getBatteryLevel() + 5));
            robotRepository.save(robot);
        }
    }

    private void tryAssignTask(Robot robot) {
        List<TransportTask> pendingTasks = taskRepository.findByStatus(TaskStatus.PENDING);
        if (pendingTasks.isEmpty())
            return;
        TransportTask task = pendingTasks.get(0);
        log.info("Assigning Task {} to Robot {}", task.getId(), robot.getName());
        task.setStatus(TaskStatus.ASSIGNED);
        task.setAssignedRobotId(robot.getId());
        taskRepository.save(task);
        robot.setCurrentTaskId(task.getId());
        robot.setState(RobotState.MOVING);
        robotRepository.save(robot);
    }

    private void sendToCharge(Robot robot) {
        // SIMULATED SEND TO CHARGE
        Optional<ChargingStation> station = stationRepository.findByIsOccupiedFalse().stream()
                .min(Comparator.comparingInt(s -> distance(robot.getX(), robot.getY(), s.getX(), s.getY())));

        if (station.isPresent()) {
            ChargingStation s = station.get();
            log.info("Assigning Charger {} to Robot {} (Simulated)", s.getName(), robot.getName());
            s.setOccupied(true);
            s.setOccupiedByRobotId(robot.getId());
            stationRepository.save(s);
            robot.setAssignedChargingStationId(s.getId());
            robot.setState(RobotState.MOVING);
            robot.setX(s.getX());
            robot.setY(s.getY());
            robot.setState(RobotState.CHARGING);
            robotRepository.save(robot);
        } else {
            log.error("No charging stations available for Robot {}!", robot.getName());
            robot.setState(RobotState.ERROR);
            robotRepository.save(robot);
        }
    }

    private int distance(int x1, int y1, int x2, int y2) {
        return Math.abs(x1 - x2) + Math.abs(y1 - y2);
    }
}
