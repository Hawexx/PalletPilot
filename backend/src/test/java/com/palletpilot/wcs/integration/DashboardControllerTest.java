package com.palletpilot.wcs.integration;

import com.palletpilot.wcs.domain.model.enums.EquipmentState;
import com.palletpilot.wcs.domain.model.enums.ProcessState;
import com.palletpilot.wcs.infrastructure.persistence.repository.EquipmentRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.MovuMissionRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.OrderRepository;
import com.palletpilot.wcs.infrastructure.web.DashboardController;
import com.palletpilot.wcs.infrastructure.web.dto.DashboardStats;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DashboardController}.
 * Validates dashboard stats aggregation logic and health status thresholds.
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
        private DashboardController dashboardController;

        // ─── Tests ──────────────────────────────────────────

        @Test
        @DisplayName("Stats — returns all expected fields with correct values")
        @SuppressWarnings("unchecked")
        void getStats_shouldReturnCorrectValues() {
                when(equipmentRepository.count()).thenReturn(10L);
                when(equipmentRepository
                                .countByStatusNotIn((Set<EquipmentState>) org.mockito.ArgumentMatchers
                                                .any(Collection.class)))
                                .thenReturn(8L);
                when(missionRepository
                                .countByStatusNotIn((List<String>) org.mockito.ArgumentMatchers.any(Collection.class)))
                                .thenReturn(5L);
                when(orderRepository.countByStatusNotIn(
                                (Set<ProcessState>) org.mockito.ArgumentMatchers.any(Collection.class)))
                                .thenReturn(3L);

                DashboardStats stats = dashboardController.getStats();

                assertThat(stats.getTotalEquipment()).isEqualTo(10);
                assertThat(stats.getOnlineEquipment()).isEqualTo(8);
                assertThat(stats.getActiveMissions()).isEqualTo(5);
                assertThat(stats.getActiveOrders()).isEqualTo(3);
                assertThat(stats.getEquipmentHealthPercentage()).isEqualTo(80.0);
                assertThat(stats.getSystemStatus()).isEqualTo("Degraded");
        }

        @Test
        @DisplayName("Stats — Operational when health >= 90%")
        @SuppressWarnings("unchecked")
        void getStats_highHealth_shouldBeOperational() {
                when(equipmentRepository.count()).thenReturn(10L);
                when(equipmentRepository
                                .countByStatusNotIn((Set<EquipmentState>) org.mockito.ArgumentMatchers
                                                .any(Collection.class)))
                                .thenReturn(10L);
                when(missionRepository
                                .countByStatusNotIn((List<String>) org.mockito.ArgumentMatchers.any(Collection.class)))
                                .thenReturn(0L);
                when(orderRepository.countByStatusNotIn(
                                (Set<ProcessState>) org.mockito.ArgumentMatchers.any(Collection.class)))
                                .thenReturn(0L);

                DashboardStats stats = dashboardController.getStats();

                assertThat(stats.getEquipmentHealthPercentage()).isEqualTo(100.0);
                assertThat(stats.getSystemStatus()).isEqualTo("Operational");
        }

        @Test
        @DisplayName("Stats — Critical when health < 50%")
        @SuppressWarnings("unchecked")
        void getStats_lowHealth_shouldBeCritical() {
                when(equipmentRepository.count()).thenReturn(10L);
                when(equipmentRepository
                                .countByStatusNotIn((Set<EquipmentState>) org.mockito.ArgumentMatchers
                                                .any(Collection.class)))
                                .thenReturn(3L);
                when(missionRepository
                                .countByStatusNotIn((List<String>) org.mockito.ArgumentMatchers.any(Collection.class)))
                                .thenReturn(0L);
                when(orderRepository.countByStatusNotIn(
                                (Set<ProcessState>) org.mockito.ArgumentMatchers.any(Collection.class)))
                                .thenReturn(0L);

                DashboardStats stats = dashboardController.getStats();

                assertThat(stats.getEquipmentHealthPercentage()).isEqualTo(30.0);
                assertThat(stats.getSystemStatus()).isEqualTo("Critical");
        }

        @Test
        @DisplayName("Stats — zero equipment returns 100% health")
        @SuppressWarnings("unchecked")
        void getStats_noEquipment_shouldReturn100Percent() {
                when(equipmentRepository.count()).thenReturn(0L);
                when(equipmentRepository
                                .countByStatusNotIn((Set<EquipmentState>) org.mockito.ArgumentMatchers
                                                .any(Collection.class)))
                                .thenReturn(0L);
                when(missionRepository
                                .countByStatusNotIn((List<String>) org.mockito.ArgumentMatchers.any(Collection.class)))
                                .thenReturn(0L);
                when(orderRepository.countByStatusNotIn(
                                (Set<ProcessState>) org.mockito.ArgumentMatchers.any(Collection.class)))
                                .thenReturn(0L);

                DashboardStats stats = dashboardController.getStats();

                assertThat(stats.getEquipmentHealthPercentage()).isEqualTo(100.0);
                assertThat(stats.getSystemStatus()).isEqualTo("Operational");
        }
}
