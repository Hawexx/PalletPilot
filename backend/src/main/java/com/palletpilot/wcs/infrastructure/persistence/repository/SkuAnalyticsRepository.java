package com.palletpilot.wcs.infrastructure.persistence.repository;

import com.palletpilot.wcs.domain.model.SkuAnalytics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SkuAnalyticsRepository extends JpaRepository<SkuAnalytics, UUID> {

    Optional<SkuAnalytics> findBySku(String sku);

    List<SkuAnalytics> findAllByOrderByTotalQuantityDesc();

    List<SkuAnalytics> findAllByOrderByTotalOrdersDesc();

    List<SkuAnalytics> findByZoneClass(String zoneClass);

    long countByZoneClass(String zoneClass);
}
