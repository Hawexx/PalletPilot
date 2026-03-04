package com.palletpilot.wcs.infrastructure.web;

import com.palletpilot.wcs.domain.model.enums.EquipmentState;
import com.palletpilot.wcs.domain.model.enums.ProcessState;
import com.palletpilot.wcs.infrastructure.persistence.repository.EquipmentRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.MovuMissionRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.OrderRepository;
import com.palletpilot.wcs.infrastructure.web.dto.DashboardStats;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Aggregated dashboard metrics")
public class DashboardController {

        private final EquipmentRepository equipmentRepository;
        private final MovuMissionRepository missionRepository;
        private final OrderRepository orderRepository;

        private static final Set<EquipmentState> OFFLINE_STATUSES = Set.of(
                        EquipmentState.OFFLINE, EquipmentState.ERROR);

        private static final Set<ProcessState> TERMINAL_STATES = Set.of(
                        ProcessState.COMPLETED, ProcessState.FAILED, ProcessState.CANCELLED);

        private static final List<String> TERMINAL_MISSION_STATUSES = List.of("COMPLETED", "FAILED");

        @GetMapping("/stats")
        @Operation(summary = "Get Dashboard Stats")
        public DashboardStats getStats() {
                // Equipment Stats — SQL count instead of loading all
                long totalEquipment = equipmentRepository.count();
                long onlineEquipment = equipmentRepository.countByStatusNotIn(OFFLINE_STATUSES);

                double health = totalEquipment > 0 ? ((double) onlineEquipment / totalEquipment) * 100.0 : 100.0;

                // Mission Stats — SQL count
                long activeMissions = missionRepository.countByStatusNotIn(TERMINAL_MISSION_STATUSES);

                // Order Stats — SQL count
                long activeOrders = orderRepository.countByStatusNotIn(TERMINAL_STATES);

                // Determine System Status
                String systemStatus = "Operational";
                if (health < 50.0) {
                        systemStatus = "Critical";
                } else if (health < 90.0) {
                        systemStatus = "Degraded";
                }

                return DashboardStats.builder()
                                .activeOrders(activeOrders)
                                .activeMissions(activeMissions)
                                .totalEquipment(totalEquipment)
                                .onlineEquipment(onlineEquipment)
                                .equipmentHealthPercentage(Math.round(health * 10.0) / 10.0)
                                .systemStatus(systemStatus)
                                .build();
        }
}
