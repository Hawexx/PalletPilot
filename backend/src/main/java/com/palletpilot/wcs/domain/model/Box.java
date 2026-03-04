package com.palletpilot.wcs.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.util.UUID;

@Entity
@Table(name = "boxes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Box {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pallet_layer_id")
    @com.fasterxml.jackson.annotation.JsonBackReference
    private PalletLayer palletLayer;

    @Column(nullable = false)
    private String sku;

    @Column(name = "x_pos_mm", nullable = false)
    private Integer xPosMm;

    @Column(name = "y_pos_mm", nullable = false)
    private Integer yPosMm;

    @Column(name = "z_pos_mm")
    private Integer zPosMm;

    @Column(name = "rotation_degrees")
    private Integer rotationDegrees;

    @Column(name = "width_mm")
    private Integer widthMm;

    @Column(name = "length_mm")
    private Integer lengthMm;

    @Column(name = "height_mm")
    private Integer heightMm;
}
