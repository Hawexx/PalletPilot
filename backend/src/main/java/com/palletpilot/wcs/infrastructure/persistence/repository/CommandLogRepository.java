package com.palletpilot.wcs.infrastructure.persistence.repository;

import com.palletpilot.wcs.domain.model.CommandLog;
import com.palletpilot.wcs.domain.model.enums.CommandStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface CommandLogRepository extends JpaRepository<CommandLog, String> {
    List<CommandLog> findByEquipmentIdAndStatus(UUID equipmentId, CommandStatus status);

    List<CommandLog> findByStatusAndCreatedAtBefore(CommandStatus status, Instant cutoff);

    /** Used by PlcCommandWorker to pick queued PLC commands in FIFO order. */
    List<CommandLog> findByStatusOrderByCreatedAtAsc(CommandStatus status);
}
