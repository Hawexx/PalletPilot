package com.palletpilot.wcs.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Defines which physical storage locations belong to which ABC zone.
 * Allows flexible zone boundaries beyond simple z-level mapping.
 *
 * Example rules:
 * - aisle 1-2, bay 1-10, z=1 → Zone A (closest to robot)
 * - aisle 1-2, bay 1-10, z=2 → Zone B
 * - aisle 3-4, any bay, z=1-2 → Zone C
 * - everything else → Zone D
 *
 * Rules are evaluated in priority order (lowest number first).
 * First matching rule wins.
 */
@Entity
@Table(name = "zone_definitions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZoneDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Zone class this rule assigns: A, B, C, or D */
    @Column(name = "zone_class", nullable = false, length = 1)
    private String zoneClass;

    /** Evaluation priority (lower = checked first). First match wins. */
    @Column(nullable = false)
    private Integer priority;

    /** Human-readable label for this rule */
    @Column(length = 100)
    private String label;

    // ── Matching Criteria (null = any / wildcard) ──

    /** Minimum aisle (inclusive). Null = no lower bound. */
    @Column(name = "aisle_min")
    private Integer aisleMin;

    /** Maximum aisle (inclusive). Null = no upper bound. */
    @Column(name = "aisle_max")
    private Integer aisleMax;

    /** Minimum bay/X position (inclusive). */
    @Column(name = "x_min")
    private Integer xMin;

    /** Maximum bay/X position (inclusive). */
    @Column(name = "x_max")
    private Integer xMax;

    /** Minimum depth/Y position (inclusive). */
    @Column(name = "y_min")
    private Integer yMin;

    /** Maximum depth/Y position (inclusive). */
    @Column(name = "y_max")
    private Integer yMax;

    /** Minimum level/Z position (inclusive). */
    @Column(name = "z_min")
    private Integer zMin;

    /** Maximum level/Z position (inclusive). */
    @Column(name = "z_max")
    private Integer zMax;

    /** Optional zone name filter (e.g., only "CASSETTE_A"). */
    @Column(name = "zone_name_filter", length = 50)
    private String zoneNameFilter;

    /** Whether this rule is active. */
    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    /**
     * Check if a storage location matches this rule's criteria.
     * Null criteria = wildcard (matches anything).
     */
    public boolean matches(StorageLocation slot) {
        if (aisleMin != null && slot.getAisle() < aisleMin)
            return false;
        if (aisleMax != null && slot.getAisle() > aisleMax)
            return false;
        if (xMin != null && slot.getX() < xMin)
            return false;
        if (xMax != null && slot.getX() > xMax)
            return false;
        if (yMin != null && slot.getY() < yMin)
            return false;
        if (yMax != null && slot.getY() > yMax)
            return false;
        if (zMin != null && slot.getZ() < zMin)
            return false;
        if (zMax != null && slot.getZ() > zMax)
            return false;
        if (zoneNameFilter != null && !zoneNameFilter.equals(slot.getZone()))
            return false;
        return true;
    }
}
