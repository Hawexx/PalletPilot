package com.palletpilot.wcs.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "alerts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alert {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "equipment_id")
    private UUID equipmentId;

    @Column(nullable = false)
    private String message;

    @Column(nullable = false)
    private String severity; // INFO, WARNING, CRITICAL

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @Builder.Default
    private boolean resolved = false;
}
