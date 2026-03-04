package com.palletpilot.wcs.application.service;

import com.palletpilot.wcs.domain.model.SkuAnalytics;
import com.palletpilot.wcs.infrastructure.persistence.repository.SkuAnalyticsRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SlottingOptimizationService}.
 * Tests SKU zone classification and analytics retrieval.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SlottingOptimizationServiceTest {

    @Mock
    private SkuAnalyticsRepository analyticsRepository;
    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private SlottingOptimizationService service;

    @BeforeEach
    void setUp() {
        // Inject @Value fields that Mockito doesn't handle
        ReflectionTestUtils.setField(service, "slottingEnabled", true);
        ReflectionTestUtils.setField(service, "defaultZone", "C");
        ReflectionTestUtils.setField(service, "defaultPeriodDays", 30);
        ReflectionTestUtils.setField(service, "classificationMode", "CUMULATIVE_SHARE");
        ReflectionTestUtils.setField(service, "rankMetric", "TOTAL_QUANTITY");
        ReflectionTestUtils.setField(service, "zoneAPercentile", 20);
        ReflectionTestUtils.setField(service, "zoneBPercentile", 30);
        ReflectionTestUtils.setField(service, "zoneCPercentile", 30);
        ReflectionTestUtils.setField(service, "zoneDPercentile", 20);
    }

    @Test
    @DisplayName("getZoneForSku — known SKU should return its zone")
    void testGetZoneForSku_known() {
        SkuAnalytics analytics = SkuAnalytics.builder()
                .sku("SKU-001")
                .zoneClass("A")
                .totalOrders(100)
                .build();
        when(analyticsRepository.findBySku("SKU-001")).thenReturn(Optional.of(analytics));

        String zone = service.getZoneForSku("SKU-001");

        assertThat(zone).isEqualTo("A");
    }

    @Test
    @DisplayName("getZoneForSku — unknown SKU should return default zone C")
    void testGetZoneForSku_unknown() {
        when(analyticsRepository.findBySku("NEW-SKU")).thenReturn(Optional.empty());

        String zone = service.getZoneForSku("NEW-SKU");

        assertThat(zone).isEqualTo("C");
    }

    @Test
    @DisplayName("getZoneForSku — slotting disabled should return default zone")
    void testGetZoneForSku_disabled() {
        ReflectionTestUtils.setField(service, "slottingEnabled", false);

        String zone = service.getZoneForSku("SKU-001");

        assertThat(zone).isEqualTo("C");
        verify(analyticsRepository, never()).findBySku(any());
    }

    @Test
    @DisplayName("getAllAnalytics — should return all analytics sorted by quantity")
    void testGetAllAnalytics() {
        SkuAnalytics a1 = SkuAnalytics.builder().sku("SKU-001").zoneClass("A").totalQuantity(500).build();
        SkuAnalytics a2 = SkuAnalytics.builder().sku("SKU-002").zoneClass("B").totalQuantity(200).build();
        when(analyticsRepository.findAllByOrderByTotalQuantityDesc()).thenReturn(List.of(a1, a2));

        List<SkuAnalytics> result = service.getAllAnalytics();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getSku()).isEqualTo("SKU-001");
    }

    @Test
    @DisplayName("getAnalyticsForSku — existing SKU should return analytics")
    void testGetAnalyticsForSku_existing() {
        SkuAnalytics analytics = SkuAnalytics.builder()
                .sku("SKU-001")
                .zoneClass("A")
                .totalOrders(100)
                .totalQuantity(500)
                .build();
        when(analyticsRepository.findBySku("SKU-001")).thenReturn(Optional.of(analytics));

        Optional<SkuAnalytics> result = service.getAnalyticsForSku("SKU-001");

        assertThat(result).isPresent();
        assertThat(result.get().getTotalOrders()).isEqualTo(100);
    }

    @Test
    @DisplayName("getAnalyticsForSku — unknown SKU should return empty")
    void testGetAnalyticsForSku_unknown() {
        when(analyticsRepository.findBySku("UNKNOWN")).thenReturn(Optional.empty());

        Optional<SkuAnalytics> result = service.getAnalyticsForSku("UNKNOWN");

        assertThat(result).isEmpty();
    }
}
