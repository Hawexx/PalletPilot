package com.palletpilot.wcs.domain.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonBackReference
    private Order order;

    @Column(nullable = false)
    private String sku;

    @Column(name = "width_mm", nullable = false)
    private Integer widthMm;

    @Column(name = "length_mm", nullable = false)
    private Integer lengthMm;

    @Column(name = "height_mm", nullable = false)
    private Integer heightMm;

    @Column(name = "weight_kg", nullable = true)
    private Double weightKg;

    @Column(nullable = false)
    private Integer quantity;
}
