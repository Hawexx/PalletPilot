package com.palletpilot.wcs.infrastructure.simulation;

import com.palletpilot.wcs.domain.port.out.HardwareClient;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * @deprecated Replaced by
 *             {@link com.palletpilot.wcs.infrastructure.sim.SimulationHardwareClient}
 *             which supports error injection, configurable delays, and
 *             correlation IDs.
 *             Kept for backward compatibility reference only — NOT a Spring
 *             bean.
 */
@Deprecated
@Slf4j
public class SimulatedHardwareClient implements HardwareClient {

    @Override
    public CompletableFuture<Boolean> sendCommand(UUID equipmentId, String command) {
        log.info("[SIMULATION-LEGACY] Sending command to {}: {}", equipmentId, command);

        return CompletableFuture.supplyAsync(() -> {
            try {
                TimeUnit.MILLISECONDS.sleep(500);
                log.info("[SIMULATION-LEGACY] Command ACK for {}", equipmentId);
                return true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        });
    }

    @Override
    public boolean checkHealth(UUID equipmentId) {
        log.debug("[SIMULATION-LEGACY] Health check OK for {}", equipmentId);
        return true;
    }
}
