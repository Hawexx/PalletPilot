package com.palletpilot.wcs.domain.model.workflow;

import com.palletpilot.wcs.domain.model.enums.ProcessState;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workflow_instances")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_definition_id")
    private WorkflowDefinition definition;

    @Column(name = "order_id")
    private UUID orderId; // Optional link to an Order

    @Column(name = "current_step_id")
    private String currentStepId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProcessState status; // RUNNING, PAUSED, COMPLETED, FAILED

    @Column(columnDefinition = "TEXT")
    private String errorDetails;

    @Column(name = "last_command_id")
    private String lastCommandId;

    /** Pallet being processed (for orchestration journeys). */
    @Column(name = "pallet_id")
    private UUID palletId;

    /** Journey type (e.g., INBOUND_TO_STORAGE, DEFAULT). */
    @Column(name = "journey_type", length = 30)
    private String journeyType;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
