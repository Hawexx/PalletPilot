package com.palletpilot.wcs.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbox pattern for durable ERP outbound confirmations.
 * Worker polls PENDING rows, sends HTTP POST, retries with backoff.
 * DLQ after max attempts.
 */
@Entity
@Table(name = "erp_outbox_messages", indexes = {
        @Index(name = "idx_outbox_status_next", columnList = "status, next_attempt_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErpOutboxMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Unique key to prevent duplicate sends: e.g. "SHIPMENT_CONFIRM:{erpOrderId}"
     */
    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "erp_order_id", nullable = false)
    private String erpOrderId;

    @Column(name = "wcs_order_id", nullable = false)
    private UUID wcsOrderId;

    /** SHIPMENT_CONFIRM, PARTIAL_CONFIRM, etc. */
    @Column(name = "message_type", nullable = false, length = 30)
    private String messageType;

    @Column(name = "target_url", nullable = false)
    private String targetUrl;

    @Column(name = "request_json", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String requestJson;

    /** PENDING → SENT | FAILED | DLQ */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Builder.Default
    @Column(name = "attempt_count")
    private int attemptCount = 0;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "response_json", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String responseJson;

    @Column(name = "http_status")
    private Integer httpStatus;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
