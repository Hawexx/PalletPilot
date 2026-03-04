package com.palletpilot.wcs.application.service;

import com.palletpilot.wcs.domain.model.ChargingStation;
import com.palletpilot.wcs.domain.model.Robot;
import com.palletpilot.wcs.domain.model.TransportTask;
import com.palletpilot.wcs.domain.model.enums.RobotState;
import com.palletpilot.wcs.domain.model.enums.TaskStatus;
import com.palletpilot.wcs.infrastructure.movu.MovuClient;
import com.palletpilot.wcs.infrastructure.persistence.repository.ChargingStationRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.EquipmentRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.RobotRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.TransportTaskRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RobotSchedulerService — scheduled robot health and task
 * assignment.
 */
@ExtendWith(MockitoExtension.class)
class RobotSchedulerServiceTest {

    @Mock
    private RobotRepository robotRepository;
    @Mock
    private ChargingStationRepository stationRepository;
    @Mock
    private TransportTaskRepository taskRepository;
    @Mock
    private ConfigurationService configurationService;
    @Mock
    private MovuClient movuClient;
    @Mock
    private EquipmentRepository equipmentRepository;

    @InjectMocks
    private RobotSchedulerService service;

    @Test
    @DisplayName("scheduleRobots: no robots → no processing")
    void schedule_noRobots_noOp() {
        when(robotRepository.findAll()).thenReturn(List.of());

        service.scheduleRobots();

        verify(taskRepository, never()).findByStatus(any());
    }

    @Test
    @DisplayName("scheduleRobots (sim): idle robot with low battery → sends to charge")
    void schedule_sim_lowBattery_sendsToCharge() {
        Robot robot = Robot.builder()
                .id(UUID.randomUUID())
                .name("R1")
                .state(RobotState.IDLE)
                .batteryLevel(15)
                .x(5).y(5).z(1)
                .build();

        ChargingStation station = ChargingStation.builder()
                .id(UUID.randomUUID())
                .name("CS-1")
                .x(0).y(0)
                .isOccupied(false)
                .build();

        when(robotRepository.findAll()).thenReturn(List.of(robot));
        when(configurationService.getBoolean("app.simulation.enabled", false)).thenReturn(true);
        when(stationRepository.findByIsOccupiedFalse()).thenReturn(List.of(station));

        service.scheduleRobots();

        // Robot should be saved with CHARGING state
        verify(robotRepository, atLeastOnce()).save(argThat(r -> ((Robot) r).getState() == RobotState.CHARGING));
    }

    @Test
    @DisplayName("scheduleRobots (sim): idle robot with good battery → assigns task")
    void schedule_sim_goodBattery_assignsTask() {
        Robot robot = Robot.builder()
                .id(UUID.randomUUID())
                .name("R2")
                .state(RobotState.IDLE)
                .batteryLevel(80)
                .x(0).y(0).z(1)
                .build();

        TransportTask task = TransportTask.builder()
                .id(UUID.randomUUID())
                .status(TaskStatus.PENDING)
                .build();

        when(robotRepository.findAll()).thenReturn(List.of(robot));
        when(configurationService.getBoolean("app.simulation.enabled", false)).thenReturn(true);
        when(taskRepository.findByStatus(TaskStatus.PENDING)).thenReturn(List.of(task));

        service.scheduleRobots();

        verify(taskRepository).save(argThat(t -> ((TransportTask) t).getStatus() == TaskStatus.ASSIGNED));
    }
}
