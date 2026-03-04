package com.palletpilot.wcs.infrastructure.persistence.repository;

import com.palletpilot.wcs.domain.model.ErpInboundMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ErpInboundMessageRepository extends JpaRepository<ErpInboundMessage, UUID> {

    Optional<ErpInboundMessage> findByMessageId(String messageId);

    boolean existsByMessageId(String messageId);

    /** Worker: pick unprocessed messages using native FOR UPDATE SKIP LOCKED */
    @Query(value = "SELECT * FROM erp_inbound_messages " +
            "WHERE status IN ('RECEIVED','FAILED') " +
            "AND (next_attempt_at IS NULL OR next_attempt_at <= :now) " +
            "ORDER BY received_at ASC LIMIT :batchSize " +
            "FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<ErpInboundMessage> findReadyForProcessing(@Param("now") Instant now, @Param("batchSize") int batchSize);

    List<ErpInboundMessage> findByStatusOrderByReceivedAtDesc(String status);

    List<ErpInboundMessage> findByErpOrderIdOrderByReceivedAtDesc(String erpOrderId);

    long countByStatus(String status);
}
