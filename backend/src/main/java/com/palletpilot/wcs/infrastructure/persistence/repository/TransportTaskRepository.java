package com.palletpilot.wcs.infrastructure.persistence.repository;

import com.palletpilot.wcs.domain.model.TransportTask;
import com.palletpilot.wcs.domain.model.enums.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TransportTaskRepository extends JpaRepository<TransportTask, UUID> {
    List<TransportTask> findByStatus(TaskStatus status);
}
