package com.palletpilot.wcs.infrastructure.persistence.repository;

import com.palletpilot.wcs.domain.model.ErpOutboxMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ErpOutboxMessageRepository extends JpaRepository<ErpOutboxMessage, UUID> {

    Optional<ErpOutboxMessage> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    /** Worker: pick pending outbox messages using native FOR UPDATE SKIP LOCKED */
    @Query(value = "SELECT * FROM erp_outbox_messages " +
            "WHERE status IN ('PENDING','FAILED') " +
            "AND next_attempt_at <= :now " +
            "ORDER BY created_at ASC LIMIT 20 " +
            "FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<ErpOutboxMessage> findReadyForSending(@Param("now") Instant now);

    List<ErpOutboxMessage> findByStatusOrderByCreatedAtDesc(String status);

    long countByStatus(String status);
}
