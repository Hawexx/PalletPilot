package com.palletpilot.wcs.infrastructure.web;

import com.palletpilot.wcs.domain.model.Robot;
import com.palletpilot.wcs.domain.model.StorageLocation;
import com.palletpilot.wcs.domain.model.TransportTask;
import com.palletpilot.wcs.infrastructure.persistence.repository.RobotRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.StorageLocationRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.TransportTaskRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@RestController
@Tag(name = "Warehouse Visualization")
@RequestMapping("/api/warehouse-view")
@RequiredArgsConstructor
public class WarehouseViewController {

    private final StorageLocationRepository storageRepository;
    private final RobotRepository robotRepository;
    private final TransportTaskRepository taskRepository;

    @GetMapping
    @Operation(summary = "Get Warehouse State", description = "Returns locations, robots, and active transport tasks")
    public ResponseEntity<WarehouseState> getState() {
        return ResponseEntity.ok(WarehouseState.builder()
                .locations(storageRepository.findAll())
                .robots(robotRepository.findAll())
                .activeTasks(taskRepository.findAll())
                .build());
    }

    @Data
    @Builder
    public static class WarehouseState {
        private List<StorageLocation> locations;
        private List<Robot> robots;
        private List<TransportTask> activeTasks;
    }
}
