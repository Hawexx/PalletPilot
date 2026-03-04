package com.palletpilot.wcs.infrastructure.persistence.repository;

import com.palletpilot.wcs.domain.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByActionOrderByTimestampDesc(String action);

    List<AuditLog> findByEntityTypeAndEntityIdOrderByTimestampDesc(String entityType, String entityId);

    List<AuditLog> findByTimestampBetweenOrderByTimestampDesc(Instant from, Instant to);
}
