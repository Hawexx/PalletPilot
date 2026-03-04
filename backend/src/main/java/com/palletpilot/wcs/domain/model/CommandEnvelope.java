package com.palletpilot.wcs.domain.model;

import com.palletpilot.wcs.domain.model.enums.CommandType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Unified command envelope for all equipment protocols.
 * Not a JPA entity — used as a transport object between services.
 *
 * Each command gets a unique commandId plus an idempotency key
 * (equipmentId + commandType + businessKey) to prevent double-execution.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommandEnvelope {

    /** Unique command identifier (UUID). */
    @Builder.Default
    private UUID commandId = UUID.randomUUID();

    /** Target equipment. */
    private UUID equipmentId;

    /** Classified command type. */
    private CommandType commandType;

    /** Protocol-specific parameters (e.g., location, speed, profile). */
    private Map<String, Object> parameters;

    /**
     * Idempotency key = equipmentId + commandType + businessKey.
     * Used to prevent duplicate command execution within a time window.
     */
    private String idempotencyKey;

    /** Correlation ID for structured logging and tracing. */
    @Builder.Default
    private String correlationId = UUID.randomUUID().toString().substring(0, 8);

    /** Business context — palletId, missionId, orderId, etc. */
    private String businessKey;

    /** When the command was created. */
    @Builder.Default
    private Instant createdAt = Instant.now();

    /** When the command should time out. */
    private Instant timeoutAt;

    /**
     * Build the idempotency key from components.
     * Format: "{equipmentId}:{commandType}:{businessKey}"
     */
    public String computeIdempotencyKey() {
        return String.format("%s:%s:%s",
                equipmentId != null ? equipmentId : "null",
                commandType != null ? commandType.name() : "null",
                businessKey != null ? businessKey : "null");
    }

    /**
     * Build a command envelope with computed defaults.
     */
    public static CommandEnvelope create(UUID equipmentId, CommandType type,
            Map<String, Object> params, String businessKey) {
        CommandEnvelope envelope = CommandEnvelope.builder()
                .equipmentId(equipmentId)
                .commandType(type)
                .parameters(params)
                .businessKey(businessKey)
                .build();
        envelope.setIdempotencyKey(envelope.computeIdempotencyKey());
        envelope.setTimeoutAt(Instant.now().plusMillis(type.getDefaultTimeoutMs()));
        return envelope;
    }
}
