package com.palletpilot.wcs.application.service;

import com.palletpilot.wcs.domain.model.SkuAnalytics;
import com.palletpilot.wcs.infrastructure.persistence.repository.SkuAnalyticsRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Core service for ABC/ABCD slotting optimization.
 * Analyzes order history to classify SKUs into demand zones (A/B/C/D).
 *
 * Classification modes:
 * - COUNT_PERCENTILE: top X% of SKUs by count → A, next Y% → B, etc.
 * - CUMULATIVE_SHARE: SKUs carrying top X% of cumulative demand → A
 * (Pareto-style)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SlottingOptimizationService {

    private final SkuAnalyticsRepository analyticsRepository;
    private final EntityManager entityManager;

    @Value("${slotting.enabled:true}")
    private boolean slottingEnabled;

    @Value("${slotting.recalculate-period-days:30}")
    private int defaultPeriodDays;

    @Value("${slotting.zone-a-percentile:20}")
    private int zoneAPercentile;

    @Value("${slotting.zone-b-percentile:30}")
    private int zoneBPercentile;

    @Value("${slotting.zone-c-percentile:30}")
    private int zoneCPercentile;

    @Value("${slotting.zone-d-percentile:20}")
    private int zoneDPercentile;

    @Value("${slotting.default-zone:C}")
    private String defaultZone;

    @Value("${slotting.classification-mode:CUMULATIVE_SHARE}")
    private String classificationMode;

    @Value("${slotting.rank-metric:TOTAL_QUANTITY}")
    private String rankMetric;

    // ─────────────────────────────────────────────
    // PUBLIC API
    // ─────────────────────────────────────────────

    /**
     * Recalculate SKU demand analytics from order history.
     * Returns a summary map: { "A": count, "B": count, ... , "durationMs": ... }
     */
    @Transactional
    public Map<String, Object> recalculateSkuAnalytics(int periodDays) {
        long startTime = System.currentTimeMillis();
        log.info("[SLOTTING] Recalculating SKU analytics for last {} days (mode={}, metric={})",
                periodDays, classificationMode, rankMetric);

        // 1. Aggregate from DB: join order_items → orders for date filtering
        Instant since = Instant.now().minus(periodDays, ChronoUnit.DAYS);
        List<Object[]> rows = querySkuAggregates(since);

        if (rows.isEmpty()) {
            log.warn("[SLOTTING] No order data found in last {} days. Keeping existing analytics unchanged.",
                    periodDays);
            return Map.of("periodDays", periodDays, "counts", Map.of(), "durationMs",
                    System.currentTimeMillis() - startTime);
        }

        // 2. Build ranked list
        List<SkuDemand> demands = new ArrayList<>();
        for (Object[] row : rows) {
            String sku = (String) row[0];
            long totalOrders = ((Number) row[1]).longValue();
            long totalQuantity = ((Number) row[2]).longValue();
            double avgDaily = (double) totalQuantity / periodDays;

            double rankValue = switch (rankMetric) {
                case "TOTAL_ORDERS" -> totalOrders;
                case "WEIGHTED" -> totalOrders * 0.3 + totalQuantity * 0.7;
                default -> totalQuantity; // TOTAL_QUANTITY
            };

            demands.add(new SkuDemand(sku, (int) totalOrders, (int) totalQuantity, avgDaily, rankValue));
        }

        // Sort descending by rank value
        demands.sort(Comparator.comparingDouble(SkuDemand::rankValue).reversed());

        // 3. Classify
        Map<String, String> classifications;
        if ("CUMULATIVE_SHARE".equals(classificationMode)) {
            classifications = classifyCumulativeShare(demands);
        } else {
            classifications = classifyCountPercentile(demands);
        }

        // 4. Upsert to DB
        Map<String, Integer> zoneCounts = new LinkedHashMap<>();
        zoneCounts.put("A", 0);
        zoneCounts.put("B", 0);
        zoneCounts.put("C", 0);
        zoneCounts.put("D", 0);

        for (SkuDemand d : demands) {
            String zoneClass = classifications.getOrDefault(d.sku(), defaultZone);
            zoneCounts.merge(zoneClass, 1, (a, b) -> a + b);

            SkuAnalytics analytics = analyticsRepository.findBySku(d.sku())
                    .orElse(SkuAnalytics.builder().sku(d.sku()).build());

            analytics.setTotalOrders(d.totalOrders());
            analytics.setTotalQuantity(d.totalQuantity());
            analytics.setAvgDailyQuantity(d.avgDaily());
            analytics.setZoneClass(zoneClass);
            analytics.setLastCalculatedAt(Instant.now());
            analytics.setPeriodDays(periodDays);
            analytics.setClassificationMode(classificationMode);
            analytics.setRankMetric(rankMetric);

            analyticsRepository.save(analytics);
        }

        long durationMs = System.currentTimeMillis() - startTime;
        log.info("[SLOTTING] Recalculation complete in {}ms. Zone counts: A={}, B={}, C={}, D={}",
                durationMs, zoneCounts.get("A"), zoneCounts.get("B"), zoneCounts.get("C"), zoneCounts.get("D"));

        return Map.of("periodDays", periodDays, "counts", zoneCounts, "durationMs", durationMs);
    }

    /**
     * Get the recommended zone class for a SKU.
     * Returns default zone if SKU has no analytics (cold start / new SKU).
     */
    @Cacheable(value = "skuAnalytics", key = "#sku")
    public String getZoneForSku(String sku) {
        if (!slottingEnabled) {
            return defaultZone;
        }
        return analyticsRepository.findBySku(sku)
                .map(SkuAnalytics::getZoneClass)
                .orElseGet(() -> {
                    log.info("[SLOTTING] No analytics for SKU '{}', using default zone '{}'", sku, defaultZone);
                    return defaultZone;
                });
    }

    /**
     * Get all analytics records, sorted by demand descending.
     */
    public List<SkuAnalytics> getAllAnalytics() {
        return analyticsRepository.findAllByOrderByTotalQuantityDesc();
    }

    /**
     * Get analytics for a specific SKU.
     */
    public Optional<SkuAnalytics> getAnalyticsForSku(String sku) {
        return analyticsRepository.findBySku(sku);
    }

    // ─────────────────────────────────────────────
    // SCHEDULED RECALCULATION
    // ─────────────────────────────────────────────

    @Scheduled(cron = "${slotting.auto-recalculate-cron:0 0 3 * * *}")
    public void scheduledRecalculation() {
        if (!slottingEnabled) {
            log.debug("[SLOTTING] Slotting disabled, skipping scheduled recalculation");
            return;
        }
        log.info("[SLOTTING] Running scheduled nightly recalculation");
        try {
            recalculateSkuAnalytics(defaultPeriodDays);
        } catch (Exception e) {
            log.error("[SLOTTING] Scheduled recalculation failed: {}", e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────
    // CLASSIFICATION ALGORITHMS
    // ─────────────────────────────────────────────

    /**
     * COUNT_PERCENTILE: Top X% of SKU count → A, next Y% → B, etc.
     */
    private Map<String, String> classifyCountPercentile(List<SkuDemand> demands) {
        Map<String, String> result = new HashMap<>();
        int total = demands.size();
        int aEnd = Math.max(1, total * zoneAPercentile / 100);
        int bEnd = aEnd + Math.max(0, total * zoneBPercentile / 100);
        int cEnd = bEnd + Math.max(0, total * zoneCPercentile / 100);

        for (int i = 0; i < total; i++) {
            String zone;
            if (i < aEnd)
                zone = "A";
            else if (i < bEnd)
                zone = "B";
            else if (i < cEnd)
                zone = "C";
            else
                zone = "D";
            result.put(demands.get(i).sku(), zone);
        }
        return result;
    }

    /**
     * CUMULATIVE_SHARE (Pareto-style): SKUs carrying top X% of total demand → A.
     * More accurate for FMCG where few SKUs drive most volume.
     *
     * Logic: classify based on cumulative share BEFORE adding this SKU.
     * This ensures the top SKU always gets Zone A.
     */
    private Map<String, String> classifyCumulativeShare(List<SkuDemand> demands) {
        Map<String, String> result = new HashMap<>();
        double totalDemand = demands.stream().mapToDouble(SkuDemand::rankValue).sum();
        if (totalDemand == 0) {
            demands.forEach(d -> result.put(d.sku(), defaultZone));
            return result;
        }

        double cumulative = 0;
        double aThreshold = totalDemand * zoneAPercentile / 100.0;
        double bThreshold = aThreshold + totalDemand * zoneBPercentile / 100.0;
        double cThreshold = bThreshold + totalDemand * zoneCPercentile / 100.0;

        for (int i = 0; i < demands.size(); i++) {
            SkuDemand d = demands.get(i);
            String zone;

            // First SKU always goes to A (Pareto leader)
            if (i == 0) {
                zone = "A";
            } else if (cumulative < aThreshold) {
                zone = "A";
            } else if (cumulative < bThreshold) {
                zone = "B";
            } else if (cumulative < cThreshold) {
                zone = "C";
            } else {
                zone = "D";
            }

            cumulative += d.rankValue();
            result.put(d.sku(), zone);
        }
        return result;
    }

    // ─────────────────────────────────────────────
    // DB AGGREGATION
    // ─────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<Object[]> querySkuAggregates(Instant since) {
        String sql = """
                SELECT oi.sku,
                       COUNT(DISTINCT o.id) AS total_orders,
                       SUM(oi.quantity) AS total_quantity
                FROM order_items oi
                JOIN orders o ON oi.order_id = o.id
                WHERE o.created_at >= :since
                GROUP BY oi.sku
                ORDER BY total_quantity DESC
                """;
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("since", since);
        return query.getResultList();
    }

    // ─────────────────────────────────────────────
    // INTERNAL TYPES
    // ─────────────────────────────────────────────

    private record SkuDemand(String sku, int totalOrders, int totalQuantity, double avgDaily, double rankValue) {
    }
}
