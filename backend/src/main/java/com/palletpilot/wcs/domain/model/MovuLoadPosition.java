package com.palletpilot.wcs.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Tracks the last known physical position of a load in the Movu warehouse.
 * Critical for Atlas 2D: the actual drop distance returned in MissionDropped
 * must be stored and reused as source.distance when the same load is retrieved.
 */
@Entity
@Table(name = "movu_load_positions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovuLoadPosition {

    /** Movu's internal load identifier (e.g. "load-9-9ijcvoeoa00lrqmkq4ohr"). */
    @Id
    @Column(name = "load_id", length = 100)
    private String loadId;

    @Column(nullable = false)
    private Integer level;

    @Column(nullable = false)
    private Integer lane;

    @Column(nullable = false)
    private Integer section;

    /** Atlas 2D: distance from front of warehouse in mm. */
    @Column(name = "distance_mm", nullable = false)
    private Integer distanceMm;

    /** The mission that last updated this position. */
    @Column(name = "last_mission_id", length = 64)
    private String lastMissionId;

    @Column(name = "last_updated")
    private Instant lastUpdated;
}
