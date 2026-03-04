package com.palletpilot.wcs.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Operational log for all ERP communications (inbound + outbound).
 * Uses JSONB for request/response to enable querying and avoid TEXT bloat.
 */
@Entity
@Table(name = "erp_integration_logs", indexes = {
        @Index(name = "idx_log_erp_order", columnList = "erp_order_id"),
        @Index(name = "idx_log_direction_created", columnList = "direction, created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErpIntegrationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** INBOUND or OUTBOUND */
    @Column(nullable = false, length = 10)
    private String direction;

    /**
     * ORDER_RECEIVED, ORDER_CONFIRMED, SHIPMENT_CONFIRMED, ERROR, DUPLICATE, etc.
     */
    @Column(name = "event_type", nullable = false, length = 30)
    private String eventType;

    @Column(name = "erp_order_id")
    private String erpOrderId;

    @Column(name = "wcs_order_id")
    private UUID wcsOrderId;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "request_json", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String requestJson;

    @Column(name = "response_json", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String responseJson;

    @Column(name = "http_status")
    private Integer httpStatus;

    /** SUCCESS, FAILED, PENDING */
    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "processed_at")
    private Instant processedAt;
}
