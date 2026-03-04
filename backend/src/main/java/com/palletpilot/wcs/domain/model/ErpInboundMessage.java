package com.palletpilot.wcs.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Every inbound ERP message is persisted here BEFORE processing.
 * UNIQUE(message_id) ensures idempotency.
 * Worker picks RECEIVED/FAILED rows via FOR UPDATE SKIP LOCKED.
 */
@Entity
@Table(name = "erp_inbound_messages", indexes = {
        @Index(name = "idx_inbound_erp_order", columnList = "erp_order_id"),
        @Index(name = "idx_inbound_status_received", columnList = "status, received_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErpInboundMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Unique message identifier — ERP-provided or WCS-generated deterministic key
     */
    @Column(name = "message_id", nullable = false, unique = true)
    private String messageId;

    @Column(name = "erp_order_id", nullable = false)
    private String erpOrderId;

    /** ORDER_CREATE, ORDER_UPDATE, ORDER_CANCEL */
    @Column(name = "event_type", nullable = false, length = 30)
    private String eventType;

    @Column(name = "warehouse_code", length = 50)
    private String warehouseCode;

    @Column(name = "correlation_id", nullable = false)
    private String correlationId;

    /** Full raw payload as JSONB for query + audit */
    @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String payloadJson;

    /** SHA-256 of payload for dedupe/audit */
    @Column(name = "payload_hash", nullable = false)
    private String payloadHash;

    /** RECEIVED → PROCESSING → PROCESSED | FAILED | DLQ */
    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** WCS order ID after successful processing */
    @Column(name = "wcs_order_id")
    private UUID wcsOrderId;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    /** For retry scheduling */
    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Builder.Default
    @Column(name = "attempt_count")
    private int attemptCount = 0;
}
