package com.palletpilot.wcs.infrastructure.web;

import com.palletpilot.wcs.domain.model.Robot;
import com.palletpilot.wcs.domain.model.StorageLocation;
import com.palletpilot.wcs.domain.model.TransportTask;
import com.palletpilot.wcs.infrastructure.persistence.repository.RobotRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.StorageLocationRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.TransportTaskRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WarehouseViewController — composite state aggregation.
 */
@ExtendWith(MockitoExtension.class)
class WarehouseViewControllerTest {

    @Mock
    private StorageLocationRepository storageRepository;
    @Mock
    private RobotRepository robotRepository;
    @Mock
    private TransportTaskRepository taskRepository;
    @InjectMocks
    private WarehouseViewController controller;

    @Test
    @DisplayName("getState: aggregates locations + robots + tasks")
    void getState_aggregatesAll() {
        StorageLocation loc = StorageLocation.builder().id(UUID.randomUUID()).build();
        Robot robot = Robot.builder().id(UUID.randomUUID()).name("R1").build();
        TransportTask task = TransportTask.builder().id(UUID.randomUUID()).build();

        when(storageRepository.findAll()).thenReturn(List.of(loc));
        when(robotRepository.findAll()).thenReturn(List.of(robot));
        when(taskRepository.findAll()).thenReturn(List.of(task));

        var resp = controller.getState();

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getLocations()).hasSize(1);
        assertThat(resp.getBody().getRobots()).hasSize(1);
        assertThat(resp.getBody().getActiveTasks()).hasSize(1);
    }

    @Test
    @DisplayName("getState: empty warehouse → empty lists (not null)")
    void getState_emptyWarehouse_emptyLists() {
        when(storageRepository.findAll()).thenReturn(List.of());
        when(robotRepository.findAll()).thenReturn(List.of());
        when(taskRepository.findAll()).thenReturn(List.of());

        var resp = controller.getState();

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getLocations()).isEmpty();
        assertThat(resp.getBody().getRobots()).isEmpty();
        assertThat(resp.getBody().getActiveTasks()).isEmpty();
    }
}
