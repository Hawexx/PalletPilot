package com.palletpilot.wcs.domain.model;

import com.palletpilot.wcs.domain.model.enums.CommandStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "command_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommandLog {

    @Id
    private String id; // UUID String

    private UUID equipmentId;

    @Column(length = 4096)
    private String commandPayload;

    @Enumerated(EnumType.STRING)
    private CommandStatus status;

    private String failureReason;

    // --- Phase 2: Unified Command Model fields ---

    /** PLC sequence ID (used for ADS handshake protocol). */
    private Long seqId;

    /** Classified command type (e.g., KUKA_PICK, CONVEYOR_START). */
    @Column(name = "command_type", length = 50)
    private String commandType;

    /** Idempotency key: equipmentId:commandType:businessKey. */
    @Column(name = "idempotency_key")
    private String idempotencyKey;

    /** Correlation ID for distributed tracing. */
    @Column(name = "correlation_id", length = 50)
    private String correlationId;

    /** When the command was acknowledged by hardware. */
    @Column(name = "ack_at")
    private Instant ackAt;

    /** When the command completed (success or failure). */
    @Column(name = "completed_at")
    private Instant completedAt;

    /** Equipment-specific error code (see ICD error code table). */
    @Column(name = "error_code")
    private Integer errorCode;

    /** Human-readable error description from hardware. */
    @Column(name = "error_text", length = 512)
    private String errorText;

    // --- Lifecycle ---

    private Instant createdAt;
    private Instant updatedAt;

    @PrePersist
    public void onCreate() {
        if (id == null)
            this.id = UUID.randomUUID().toString();
        if (createdAt == null)
            this.createdAt = Instant.now();
        if (updatedAt == null)
            this.updatedAt = Instant.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
