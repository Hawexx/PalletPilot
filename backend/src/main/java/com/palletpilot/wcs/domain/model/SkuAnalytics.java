package com.palletpilot.wcs.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Stores per-SKU demand analytics for ABC/ABCD zone classification.
 * Recalculated periodically (default nightly) from order history.
 */
@Entity
@Table(name = "sku_analytics", indexes = {
        @Index(name = "idx_sku_analytics_zone_class", columnList = "zone_class")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SkuAnalytics {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 128)
    private String sku;

    @Column(name = "total_orders", nullable = false)
    private int totalOrders;

    @Column(name = "total_quantity", nullable = false)
    private int totalQuantity;

    @Column(name = "avg_daily_quantity", nullable = false)
    private double avgDailyQuantity;

    /**
     * Zone classification: A (highest demand), B, C, D (lowest demand).
     */
    @Column(name = "zone_class", nullable = false, length = 1)
    private String zoneClass;

    @Column(name = "last_calculated_at", nullable = false)
    private Instant lastCalculatedAt;

    @Column(name = "period_days", nullable = false)
    private int periodDays;

    @Column(name = "classification_mode", nullable = false, length = 32)
    private String classificationMode;

    @Column(name = "rank_metric", nullable = false, length = 32)
    private String rankMetric;
}
