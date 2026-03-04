package com.palletpilot.wcs.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a single step in a pallet journey orchestration.
 * Each step maps to one equipment command execution.
 * Persisted for resume-on-restart safety.
 *
 * States: PENDING → RUNNING → COMPLETED | FAILED | SKIPPED | COMPENSATED
 */
@Entity
@Table(name = "journey_steps")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JourneyStep {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Parent orchestration instance. */
    @Column(name = "journey_id", nullable = false)
    private UUID journeyId;

    /** Execution order (0-based). */
    @Column(name = "step_index", nullable = false)
    private int stepIndex;

    /** Command type to execute (e.g., KUKA_PICK, CONVEYOR_START). */
    @Column(name = "command_type", nullable = false, length = 50)
    private String commandType;

    /** Target equipment. Null for virtual steps (zone transitions). */
    @Column(name = "equipment_id")
    private UUID equipmentId;

    /** Step status: PENDING, RUNNING, COMPLETED, FAILED, SKIPPED, COMPENSATED. */
    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "PENDING";

    /** Command ID once command is dispatched (links to CommandLog). */
    @Column(name = "command_id")
    private String commandId;

    /** Compensation action to execute on rollback (e.g., CONVEYOR_STOP). */
    @Column(name = "compensation_action", length = 50)
    private String compensationAction;

    /** JSON parameters for the command. */
    @Column(columnDefinition = "TEXT")
    private String parameters;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "error_details", columnDefinition = "TEXT")
    private String errorDetails;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    // --- State transitions ---

    public boolean isPending() {
        return "PENDING".equals(status);
    }

    public boolean isRunning() {
        return "RUNNING".equals(status);
    }

    public boolean isCompleted() {
        return "COMPLETED".equals(status);
    }

    public boolean isFailed() {
        return "FAILED".equals(status);
    }

    public boolean isTerminal() {
        return "COMPLETED".equals(status) || "FAILED".equals(status)
                || "SKIPPED".equals(status) || "COMPENSATED".equals(status);
    }

    public void markRunning(String commandId) {
        this.status = "RUNNING";
        this.commandId = commandId;
        this.startedAt = Instant.now();
    }

    public void markCompleted() {
        this.status = "COMPLETED";
        this.completedAt = Instant.now();
    }

    public void markFailed(String error) {
        this.status = "FAILED";
        this.errorDetails = error;
        this.completedAt = Instant.now();
    }

    public void markCompensated() {
        this.status = "COMPENSATED";
        this.completedAt = Instant.now();
    }
}
