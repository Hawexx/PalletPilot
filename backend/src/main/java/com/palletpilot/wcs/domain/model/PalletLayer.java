package com.palletpilot.wcs.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

@Entity
@Table(name = "pallet_layers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PalletLayer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pallet_id")
    @com.fasterxml.jackson.annotation.JsonBackReference
    private Pallet pallet;

    @Column(name = "layer_index", nullable = false)
    private Integer layerIndex;

    @Column(name = "z_height_mm", nullable = false)
    private Integer zHeightMm;

    @Column(name = "layer_weight_kg")
    private Double layerWeightKg;

    @Column(name = "layer_center_x_mm")
    private Double layerCenterX;

    @Column(name = "layer_center_y_mm")
    private Double layerCenterY;

    @OneToMany(mappedBy = "palletLayer", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    @com.fasterxml.jackson.annotation.JsonManagedReference
    private List<Box> boxes = new ArrayList<>();

    // Helper to add boxes would go here, updating dynamic weights
}
