package com.palletpilot.wcs.domain.model;

import com.palletpilot.wcs.domain.model.enums.ProcessState;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.util.List;
import java.util.UUID;
import java.time.Instant;

@Entity
@Table(name = "pallets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pallet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id")
    private UUID orderId;

    @Builder.Default
    private String type = "EPAL3"; // 1000x1200mm

    @Column(name = "pallet_number")
    private Integer palletNumber; // Sequential within order (1, 2, 3...)

    @Column(name = "pallet_display_id")
    private String palletDisplayId; // "Pallet-1", "Pallet-2", etc.

    @Column(name = "total_box_count")
    private Integer totalBoxCount; // Total boxes on this pallet (max 40)

    @Column(name = "layer_count")
    private Integer layerCount; // Number of layers (max 4)

    @Enumerated(EnumType.STRING)
    @Column(name = "process_state", nullable = false)
    private ProcessState processState;

    @Column(name = "stability_score")
    private Double stabilityScore;

    @Column(name = "cog_offset_x_mm")
    private Double cogOffsetX;

    @Column(name = "cog_offset_y_mm")
    private Double cogOffsetY;

    @Column(name = "total_weight_kg")
    private Double totalWeightKg;

    @Column(name = "volume_efficiency")
    private Double volumeEfficiency; // Density % (0-100)

    @Column(name = "current_location")
    private String currentLocation; // Descriptive text (e.g. "Inbound", "Rack 1-2-1")

    @Column(name = "storage_location_id")
    private UUID storageLocationId;

    @Column(name = "held_by_robot_id")
    private UUID heldByRobotId;

    @OneToMany(mappedBy = "pallet", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @com.fasterxml.jackson.annotation.JsonManagedReference
    private List<PalletLayer> layers;

    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();

    public void calculateMetrics() {
        if (layers == null || layers.isEmpty()) {
            this.totalWeightKg = 0.0;
            this.cogOffsetX = 0.0;
            this.cogOffsetY = 0.0;
            return;
        }

        double totalWeight = 0.0;
        double weightedX = 0.0;
        double weightedY = 0.0;

        for (PalletLayer layer : layers) {
            totalWeight += layer.getLayerWeightKg();
            // Assuming layer CoG is relative to pallet center, we weight it
            weightedX += layer.getLayerCenterX() * layer.getLayerWeightKg();
            weightedY += layer.getLayerCenterY() * layer.getLayerWeightKg();
        }

        this.totalWeightKg = totalWeight;
        this.cogOffsetX = totalWeight > 0 ? weightedX / totalWeight : 0.0;
        this.cogOffsetY = totalWeight > 0 ? weightedY / totalWeight : 0.0;
    }
}
