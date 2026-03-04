package com.palletpilot.wcs.infrastructure.persistence;

import com.palletpilot.wcs.application.service.ZoneDefinitionService;
import com.palletpilot.wcs.domain.model.ChargingStation;
import com.palletpilot.wcs.domain.model.Robot;
import com.palletpilot.wcs.domain.model.StorageLocation;
import com.palletpilot.wcs.domain.model.enums.RobotState;
import com.palletpilot.wcs.infrastructure.persistence.repository.ChargingStationRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.RobotRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.StorageLocationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class LayoutSeeder implements CommandLineRunner {

    private final StorageLocationRepository storageRepository;
    private final RobotRepository robotRepository;
    private final ChargingStationRepository stationRepository;
    private final ZoneDefinitionService zoneDefinitionService;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        // Always seed zone definitions (idempotent)
        zoneDefinitionService.seedDefaultDefinitions();

        if (storageRepository.count() > 0) {
            log.info("Layout already seeded. Skipping.");
            return;
        }

        log.info("Seeding Warehouse Layout...");

        // 1. Seed Storage Cassettes
        // Layout: 4 Aisles, 20 Bays long (X), 4 Levels High (Z), 2 Deep (Y)
        // Just an approximation of the image provided.
        int aisles = 4;
        int bays = 20;
        int levels = 4;

        for (int a = 1; a <= aisles; a++) {
            String zone = "AISLE-" + a;
            for (int x = 1; x <= bays; x++) {
                for (int z = 1; z <= levels; z++) {
                    // Create deep storage slot (depth 1)
                    createLocation(zone, a, x, 1, z);
                    // Create deep storage slot (depth 2)
                    createLocation(zone, a, x, 2, z);
                }
            }
        }

        // 2. Seed Charging Stations
        // 4 Charging stations near the front
        createChargingStation("CHARGER-01", 1, 0);
        createChargingStation("CHARGER-02", 2, 0);
        createChargingStation("CHARGER-03", 3, 0);
        createChargingStation("CHARGER-04", 4, 0);

        // 3. Seed Robots
        // 4 Robots to start
        createRobot("ROBOT-01", 1, 0);
        createRobot("ROBOT-02", 2, 0);
        createRobot("ROBOT-03", 3, 0);
        createRobot("ROBOT-04", 4, 0);

        log.info("Warehouse Layout Seeded Successfully.");
    }

    private void createLocation(String zone, int aisle, int x, int y, int z) {
        // Zone class mapping: lower Z = faster robot access = higher priority zone
        String zoneClass = switch (z) {
            case 1 -> "A"; // Fastest access — highest demand SKUs
            case 2 -> "B";
            case 3 -> "C";
            case 4 -> "D"; // Slowest access — lowest demand SKUs
            default -> "C"; // Safe default
        };

        StorageLocation loc = StorageLocation.builder()
                .zone(zone)
                .zoneClass(zoneClass)
                .aisle(aisle)
                .x(x)
                .y(y)
                .z(z)
                .isOccupied(false)
                .maxWeightKg(1000.0) // 1 Ton per slot
                .maxHeightMm(2000) // 2 Meters
                .build();
        storageRepository.save(loc);
    }

    private void createChargingStation(String name, int x, int y) {
        ChargingStation station = ChargingStation.builder()
                .name(name)
                .x(x)
                .y(y)
                .isOccupied(false)
                .build();
        stationRepository.save(station);
    }

    private void createRobot(String name, int x, int y) {
        Robot robot = Robot.builder()
                .name(name)
                .x(x)
                .y(y)
                .z(0)
                .batteryLevel(100)
                .state(RobotState.IDLE)
                .build();
        robotRepository.save(robot);
    }
}
