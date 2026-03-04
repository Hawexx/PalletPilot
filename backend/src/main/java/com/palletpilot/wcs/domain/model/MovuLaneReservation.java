package com.palletpilot.wcs.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Reserves a specific distance slot in a lane for a mission.
 * Prevents two concurrent missions from choosing the same drop position.
 *
 * CRITICAL for physical safety: without reservation, two shuttles
 * could be directed to the same distance, causing a collision.
 *
 * Reservation lifecycle:
 * - Created when late decision computes a distance
 * - Cleared when mission completes (MissionDropped) or is aborted/cancelled
 * - Expires after TTL as safety net
 */
@Entity
@Table(name = "movu_lane_reservations", uniqueConstraints = @UniqueConstraint(name = "uk_lane_reservation_slot", columnNames = {
        "equipment_id", "level", "lane", "section", "distance_mm" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovuLaneReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    @Column(nullable = false)
    private int level;

    @Column(nullable = false)
    private int lane;

    @Column(nullable = false)
    private int section;

    @Column(name = "distance_mm", nullable = false)
    private int distanceMm;

    @Column(name = "mission_id", nullable = false)
    private String missionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "confirmation_type", nullable = false)
    private MovuPendingConfirmation.ConfirmationType confirmationType;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
