package com.palletpilot.wcs.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Tracks pending entrance confirmations for Movu late decision flow.
 * When aSTC sends MissionAwaitingDestination/SourceEntryConfirmation,
 * a row is created here. A scheduled job processes pending rows.
 *
 * DB locking (SELECT FOR UPDATE SKIP LOCKED) ensures at-most-one
 * worker processes each confirmation, preventing race conditions.
 */
@Entity
@Table(name = "movu_pending_confirmations", uniqueConstraints = @UniqueConstraint(name = "uk_pending_mission_type", columnNames = {
        "mission_id", "confirmation_type" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovuPendingConfirmation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    @Column(name = "mission_id", nullable = false)
    private String missionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "confirmation_type", nullable = false)
    private ConfirmationType confirmationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ConfirmationStatus status = ConfirmationStatus.PENDING;

    @Column(name = "requested_at", nullable = false)
    @Builder.Default
    private Instant requestedAt = Instant.now();

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "attempt_count")
    @Builder.Default
    private int attemptCount = 0;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "reserved_distance_mm")
    private Integer reservedDistanceMm;

    @Column
    private Integer lane;

    @Column
    private Integer level;

    @Column
    private Integer section;

    @Column(name = "error_message")
    private String errorMessage;

    public enum ConfirmationType {
        SOURCE, DESTINATION
    }

    public enum ConfirmationStatus {
        PENDING, IN_PROGRESS, SUCCEEDED, FAILED, ABORTED
    }
}
