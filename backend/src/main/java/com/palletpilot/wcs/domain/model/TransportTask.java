package com.palletpilot.wcs.domain.model;

import com.palletpilot.wcs.domain.model.enums.TaskStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transport_tasks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransportTask {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "pallet_id", nullable = false)
    private UUID palletId;

    @Column(name = "source_location_id")
    private UUID sourceLocationId; // Null if coming from Inbound (or treat Inbound as a loc)

    @Column(name = "target_location_id", nullable = false)
    private UUID targetLocationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status; // PENDING, ASSIGNED, IN_PROGRESS, COMPLETED, FAILED

    @Column(name = "assigned_robot_id")
    private UUID assignedRobotId;

    @CreationTimestamp
    private Instant createdAt;
}
