package com.palletpilot.wcs.infrastructure.web;

import com.palletpilot.wcs.domain.model.enums.EquipmentState;
import com.palletpilot.wcs.domain.model.enums.ProcessState;
import com.palletpilot.wcs.infrastructure.persistence.repository.EquipmentRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.MovuMissionRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.OrderRepository;
import com.palletpilot.wcs.infrastructure.web.dto.DashboardStats;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DashboardController — metrics aggregation with health tiers.
 */
@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    @Mock
    private EquipmentRepository equipmentRepository;
    @Mock
    private MovuMissionRepository missionRepository;
    @Mock
    private OrderRepository orderRepository;
    @InjectMocks
    private DashboardController controller;

    @Test
    @DisplayName("all equipment online → 100% health → Operational")
    void allOnline_operational() {
        when(equipmentRepository.count()).thenReturn(10L);
        when(equipmentRepository.countByStatusNotIn(any())).thenReturn(10L);
        when(missionRepository.countByStatusNotIn(any())).thenReturn(3L);
        when(orderRepository.countByStatusNotIn(any())).thenReturn(5L);

        DashboardStats stats = controller.getStats();

        assertThat(stats.getEquipmentHealthPercentage()).isEqualTo(100.0);
        assertThat(stats.getSystemStatus()).isEqualTo("Operational");
        assertThat(stats.getActiveMissions()).isEqualTo(3L);
        assertThat(stats.getActiveOrders()).isEqualTo(5L);
        assertThat(stats.getTotalEquipment()).isEqualTo(10L);
        assertThat(stats.getOnlineEquipment()).isEqualTo(10L);
    }

    @Test
    @DisplayName("70% health → Degraded")
    void seventyPercent_degraded() {
        when(equipmentRepository.count()).thenReturn(10L);
        when(equipmentRepository.countByStatusNotIn(any())).thenReturn(7L);
        when(missionRepository.countByStatusNotIn(any())).thenReturn(0L);
        when(orderRepository.countByStatusNotIn(any())).thenReturn(0L);

        DashboardStats stats = controller.getStats();

        assertThat(stats.getEquipmentHealthPercentage()).isEqualTo(70.0);
        assertThat(stats.getSystemStatus()).isEqualTo("Degraded");
    }

    @Test
    @DisplayName("30% health → Critical")
    void thirtyPercent_critical() {
        when(equipmentRepository.count()).thenReturn(10L);
        when(equipmentRepository.countByStatusNotIn(any())).thenReturn(3L);
        when(missionRepository.countByStatusNotIn(any())).thenReturn(0L);
        when(orderRepository.countByStatusNotIn(any())).thenReturn(0L);

        DashboardStats stats = controller.getStats();

        assertThat(stats.getEquipmentHealthPercentage()).isEqualTo(30.0);
        assertThat(stats.getSystemStatus()).isEqualTo("Critical");
    }

    @Test
    @DisplayName("zero equipment → 100% health (safe division)")
    void zeroEquipment_safeDivision() {
        when(equipmentRepository.count()).thenReturn(0L);
        when(equipmentRepository.countByStatusNotIn(any())).thenReturn(0L);
        when(missionRepository.countByStatusNotIn(any())).thenReturn(0L);
        when(orderRepository.countByStatusNotIn(any())).thenReturn(0L);

        DashboardStats stats = controller.getStats();

        assertThat(stats.getEquipmentHealthPercentage()).isEqualTo(100.0);
        assertThat(stats.getSystemStatus()).isEqualTo("Operational");
    }

    @Test
    @DisplayName("boundary: exactly 90% → Operational (not Degraded)")
    void ninetyPercent_stillOperational() {
        when(equipmentRepository.count()).thenReturn(10L);
        when(equipmentRepository.countByStatusNotIn(any())).thenReturn(9L);
        when(missionRepository.countByStatusNotIn(any())).thenReturn(0L);
        when(orderRepository.countByStatusNotIn(any())).thenReturn(0L);

        DashboardStats stats = controller.getStats();

        assertThat(stats.getEquipmentHealthPercentage()).isEqualTo(90.0);
        assertThat(stats.getSystemStatus()).isEqualTo("Operational");
    }

    @Test
    @DisplayName("boundary: exactly 50% → Degraded (not Critical)")
    void fiftyPercent_degraded() {
        when(equipmentRepository.count()).thenReturn(10L);
        when(equipmentRepository.countByStatusNotIn(any())).thenReturn(5L);
        when(missionRepository.countByStatusNotIn(any())).thenReturn(0L);
        when(orderRepository.countByStatusNotIn(any())).thenReturn(0L);

        DashboardStats stats = controller.getStats();

        assertThat(stats.getEquipmentHealthPercentage()).isEqualTo(50.0);
        assertThat(stats.getSystemStatus()).isEqualTo("Degraded");
    }
}
