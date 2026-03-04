package com.palletpilot.wcs.application.service;

import com.palletpilot.wcs.domain.model.enums.EquipmentState;
import com.palletpilot.wcs.domain.model.enums.ProcessState;
import com.palletpilot.wcs.infrastructure.persistence.repository.EquipmentRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.MovuMissionRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Periodically broadcasts dashboard stats via WebSocket.
 * This enables real-time dashboard updates without polling.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DashboardBroadcastScheduler {

        private final EquipmentRepository equipmentRepository;
        private final MovuMissionRepository missionRepository;
        private final OrderRepository orderRepository;
        private final WebSocketBroadcastService wsBroadcast;

        private static final Set<EquipmentState> OFFLINE_STATUSES = Set.of(
                        EquipmentState.OFFLINE, EquipmentState.ERROR);

        private static final Set<ProcessState> TERMINAL_STATES = Set.of(
                        ProcessState.COMPLETED, ProcessState.FAILED, ProcessState.CANCELLED);

        private static final List<String> TERMINAL_MISSION_STATUSES = List.of("COMPLETED", "FAILED");

        @Scheduled(fixedDelay = 5000) // Every 5 seconds
        public void broadcastDashboardStats() {
                try {
                        long totalEquipment = equipmentRepository.count();
                        long onlineEquipment = equipmentRepository.countByStatusNotIn(OFFLINE_STATUSES);

                        double health = totalEquipment > 0
                                        ? ((double) onlineEquipment / totalEquipment) * 100.0
                                        : 100.0;

                        long activeMissions = missionRepository.countByStatusNotIn(TERMINAL_MISSION_STATUSES);

                        long activeOrders = orderRepository.countByStatusNotIn(TERMINAL_STATES);

                        String systemStatus = health < 50.0 ? "Critical"
                                        : (health < 90.0 ? "Degraded" : "Operational");

                        wsBroadcast.sendDashboardUpdate(Map.of(
                                        "activeOrders", activeOrders,
                                        "activeMissions", activeMissions,
                                        "totalEquipment", totalEquipment,
                                        "onlineEquipment", onlineEquipment,
                                        "equipmentHealthPercentage", Math.round(health * 10.0) / 10.0,
                                        "systemStatus", systemStatus));
                } catch (Exception e) {
                        // Scheduler failure must not crash the application
                        log.debug("[DASHBOARD-WS] Broadcast failed: {}", e.getMessage());
                }
        }
}
