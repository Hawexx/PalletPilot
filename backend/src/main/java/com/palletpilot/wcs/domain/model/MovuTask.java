package com.palletpilot.wcs.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Tracks dock/undock task lifecycle for shuttle roaming between levels.
 * When a shuttle needs to move to a different level, it must:
 * 1. Dock onto the lift (POST /tasks/dock)
 * 2. Wait for lift to move (PLC integration)
 * 3. Undock from the lift (POST /tasks/undock)
 *
 * Each step generates TaskStateChanged webhooks from aSTC.
 */
@Entity
@Table(name = "movu_tasks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovuTask {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    /** Task ID from aSTC (if provided), else locally generated. */
    @Column(name = "task_id", unique = true, length = 64)
    private String taskId;

    @Column(name = "vehicle_id", nullable = false)
    private int vehicleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false)
    private TaskType taskType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TaskState state = TaskState.NOT_ASSIGNED;

    @Column(name = "source_level")
    private Integer sourceLevel;

    @Column(name = "target_level")
    private Integer targetLevel;

    @Column(name = "lift_section")
    private Integer liftSection;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @Column(name = "last_event_at")
    private Instant lastEventAt;

    public enum TaskType {
        DOCK, UNDOCK
    }

    public enum TaskState {
        NOT_ASSIGNED, ASSIGNED, MOVING_SHUTTLE, WAIT_FOR_HANDOVER,
        DONE, FAILED, TIMEOUT
    }
}
