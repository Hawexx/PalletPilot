package com.palletpilot.wcs.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "storage_locations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StorageLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String zone; // e.g., "CASSETTE_A", "OUTBOUND_BUFFER"

    /**
     * ABC/ABCD zone classification for slotting optimization.
     * A = highest demand (fastest access), D = lowest demand.
     * Assigned by LayoutSeeder based on Z-level and updated by
     * SlottingOptimizationService.
     */
    @Column(name = "zone_class", length = 1)
    private String zoneClass;

    @Column
    private Integer aisle;

    // Logical coordinates
    @Column(nullable = false)
    private Integer x; // Cell index along aisle

    @Column(nullable = false)
    private Integer y; // Depth index (if multi-deep) or just 0

    @Column(nullable = false)
    private Integer z; // Vertical Level (1-4)

    @Column(name = "is_occupied", nullable = false)
    private boolean isOccupied;

    @Column(name = "stored_pallet_id")
    private UUID storedPalletId;

    // Physical dimensions constraint (optional for now, but good for realism)
    @Column(name = "max_height_mm")
    private Integer maxHeightMm;

    @Column(name = "max_weight_kg")
    private Double maxWeightKg;
}
