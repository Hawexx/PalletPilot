package com.palletpilot.wcs.application.service;

import com.palletpilot.wcs.domain.model.MovuTask;
import com.palletpilot.wcs.domain.model.MovuTask.TaskState;
import com.palletpilot.wcs.domain.model.MovuTask.TaskType;
import com.palletpilot.wcs.infrastructure.movu.MovuClient;
import com.palletpilot.wcs.infrastructure.persistence.repository.MovuTaskRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ShuttleRoamingService — dock/undock/lift orchestration.
 * Tests initiation, state change handling, dock completion, and watchdog.
 */
@ExtendWith(MockitoExtension.class)
class ShuttleRoamingServiceTest {

    @Mock
    private MovuClient movuClient;
    @Mock
    private MovuTaskRepository taskRepository;
    @Mock
    private AlertService alertService;

    @InjectMocks
    private ShuttleRoamingService service;

    @Test
    @DisplayName("initiateRoaming: no active tasks → creates DOCK and sends dockShuttle")
    void initiateRoaming_createsAndSendsDock() {
        UUID eqId = UUID.randomUUID();
        when(taskRepository.findByEquipmentIdAndVehicleIdAndStateIn(eq(eqId), eq(1), any()))
                .thenReturn(List.of());
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MovuTask result = service.initiateRoaming(eqId, 1, 3, 5, 1);

        verify(movuClient).dockShuttle(eq(eqId), any());
        verify(taskRepository, atLeast(2)).save(any(MovuTask.class));
    }

    @Test
    @DisplayName("initiateRoaming: active tasks exist → throws")
    void initiateRoaming_activeTasks_throws() {
        UUID eqId = UUID.randomUUID();
        MovuTask existing = MovuTask.builder().equipmentId(eqId).vehicleId(1)
                .taskType(TaskType.DOCK).build();
        when(taskRepository.findByEquipmentIdAndVehicleIdAndStateIn(eq(eqId), eq(1), any()))
                .thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.initiateRoaming(eqId, 1, 3, 5, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already has active roaming tasks");
    }

    @Test
    @DisplayName("handleTaskStateChanged: unknown taskId → ignored")
    void handleTaskStateChanged_unknownTask_ignored() {
        UUID eqId = UUID.randomUUID();
        when(taskRepository.findByTaskId("UNKNOWN")).thenReturn(Optional.empty());

        service.handleTaskStateChanged("UNKNOWN", "Done", eqId);

        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("handleTaskStateChanged: dock completed → creates UNDOCK")
    void handleTaskStateChanged_dockDone_createsUndock() {
        UUID eqId = UUID.randomUUID();
        MovuTask dockTask = MovuTask.builder()
                .equipmentId(eqId)
                .vehicleId(1)
                .taskType(TaskType.DOCK)
                .sourceLevel(3)
                .targetLevel(5)
                .liftSection(1)
                .state(TaskState.ASSIGNED)
                .build();

        when(taskRepository.findByTaskId("DOCK-1")).thenReturn(Optional.of(dockTask));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.handleTaskStateChanged("DOCK-1", "Done", eqId);

        verify(movuClient).undockShuttle(eq(eqId), any());
        // Should have saved dock state + undock task + undock state
        verify(taskRepository, atLeast(3)).save(any(MovuTask.class));
    }

    @Test
    @DisplayName("watchStuckTasks: stuck task → TIMEOUT + CRITICAL alert")
    void watchStuckTasks_stuckTask_alerts() {
        UUID eqId = UUID.randomUUID();
        MovuTask stuck = MovuTask.builder()
                .equipmentId(eqId)
                .vehicleId(2)
                .taskType(TaskType.DOCK)
                .state(TaskState.ASSIGNED)
                .updatedAt(Instant.now().minusSeconds(120))
                .build();

        when(taskRepository.findByStateInAndUpdatedAtBefore(any(), any())).thenReturn(List.of(stuck));

        service.watchStuckTasks();

        assertThat(stuck.getState()).isEqualTo(TaskState.TIMEOUT);
        verify(alertService).raiseAlert(eq(eqId), contains("timed out"), eq("CRITICAL"));
    }
}
