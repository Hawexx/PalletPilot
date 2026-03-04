package com.palletpilot.wcs.infrastructure.config;

import com.palletpilot.wcs.domain.model.enums.EquipmentState;
import com.palletpilot.wcs.infrastructure.persistence.repository.EquipmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Custom health indicator for PLC/Equipment system.
 * Exposes equipment status under /actuator/health → "plc".
 */
@Component("plc")
@RequiredArgsConstructor
@Slf4j
public class PlcHealthIndicator implements HealthIndicator {

        private final EquipmentRepository equipmentRepository;

        @Override
        public Health health() {
                try {
                        long total = equipmentRepository.count();
                        if (total == 0) {
                                return Health.unknown()
                                                .withDetail("message", "No equipment registered")
                                                .build();
                        }

                        long online = equipmentRepository.countByStatusNotIn(
                                        Set.of(EquipmentState.OFFLINE, EquipmentState.ERROR));

                        long error = equipmentRepository.countByStatusIn(
                                        Set.of(EquipmentState.ERROR));

                        double healthPct = ((double) online / total) * 100.0;

                        Health.Builder builder = healthPct < 50.0
                                        ? Health.down()
                                        : (healthPct < 90.0 ? Health.outOfService() : Health.up());

                        return builder
                                        .withDetail("totalEquipment", total)
                                        .withDetail("onlineEquipment", online)
                                        .withDetail("errorEquipment", error)
                                        .withDetail("healthPercentage", Math.round(healthPct * 10.0) / 10.0)
                                        .build();
                } catch (Exception e) {
                        log.warn("[HEALTH] PLC health check failed: {}", e.getMessage());
                        return Health.down()
                                        .withDetail("error", e.getMessage())
                                        .build();
                }
        }
}
