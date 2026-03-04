package com.palletpilot.wcs.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "movu_missions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovuMission {

    @Id
    @Column(length = 64)
    private String id; // Mission ID from Movu

    @Column(name = "equipment_id")
    private UUID equipmentId;

    @Column(nullable = false)
    private String sku;

    @Column(nullable = false)
    private String sourceLocation;

    @Column(nullable = false)
    private String targetLocation;

    @Column(nullable = false)
    private String status; // CREATED, RUNNING, COMPLETED, FAILED

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
