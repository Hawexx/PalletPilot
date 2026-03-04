package com.palletpilot.wcs.infrastructure.config;

import com.palletpilot.wcs.infrastructure.persistence.repository.MovuMissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Custom health indicator for Movu shuttle system.
 * Exposes shuttle status under /actuator/health → "movu".
 */
@Component("movu")
@RequiredArgsConstructor
@Slf4j
public class MovuHealthIndicator implements HealthIndicator {

        private final MovuMissionRepository missionRepository;

        @Override
        public Health health() {
                try {
                        long totalMissions = missionRepository.count();
                        long activeMissions = missionRepository.countByStatusNotIn(
                                        List.of("COMPLETED", "FAILED"));
                        long failedMissions = missionRepository.countByStatus("FAILED");

                        Health.Builder builder = failedMissions > 5
                                        ? Health.down()
                                        : (activeMissions > 20 ? Health.outOfService() : Health.up());

                        return builder
                                        .withDetail("totalMissions", totalMissions)
                                        .withDetail("activeMissions", activeMissions)
                                        .withDetail("failedMissions", failedMissions)
                                        .build();
                } catch (Exception e) {
                        log.warn("[HEALTH] Movu health check failed: {}", e.getMessage());
                        return Health.down()
                                        .withDetail("error", e.getMessage())
                                        .build();
                }
        }
}
