package com.palletpilot.wcs.infrastructure.persistence.repository;

import com.palletpilot.wcs.domain.model.MovuTask;
import com.palletpilot.wcs.domain.model.MovuTask.TaskState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MovuTaskRepository extends JpaRepository<MovuTask, UUID> {

    Optional<MovuTask> findByTaskId(String taskId);

    /** Find tasks stuck in active states for watchdog monitoring. */
    List<MovuTask> findByStateInAndUpdatedAtBefore(List<TaskState> states, Instant cutoff);

    /** Find active tasks for a specific vehicle. */
    List<MovuTask> findByEquipmentIdAndVehicleIdAndStateIn(
            UUID equipmentId, int vehicleId, List<TaskState> states);
}
