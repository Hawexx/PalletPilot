package com.palletpilot.wcs.infrastructure.sim;

import com.palletpilot.wcs.domain.port.out.HardwareClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enhanced simulation client for hardware testing.
 * Supports configurable delays, error injection, and PLC state machine
 * emulation.
 */
@Component("simulationClient")
@Slf4j
public class SimulationHardwareClient implements HardwareClient {

    /** Error injection map: equipmentId → errorCode. Set value to inject error. */
    private final Map<UUID, Integer> injectedErrors = new ConcurrentHashMap<>();

    /** Delay injection map: equipmentId → delayMs. */
    private final Map<UUID, Long> injectedDelays = new ConcurrentHashMap<>();

    /** Global simulation delay (ms). */
    private long defaultDelayMs = 500;

    @Override
    public CompletableFuture<Boolean> sendCommand(UUID equipmentId, String commandString) {
        String correlationId = UUID.randomUUID().toString().substring(0, 8);

        return CompletableFuture.supplyAsync(() -> {
            long delay = injectedDelays.getOrDefault(equipmentId, defaultDelayMs);

            log.info("[SIM] cid={} equipment={} cmd={} delay={}ms",
                    correlationId, equipmentId, commandString, delay);

            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }

            // Check for injected error
            Integer errorCode = injectedErrors.remove(equipmentId);
            if (errorCode != null) {
                log.warn("[SIM] cid={} equipment={} INJECTED ERROR code={}",
                        correlationId, equipmentId, errorCode);
                return false;
            }

            log.info("[SIM] cid={} equipment={} DONE", correlationId, equipmentId);
            return true;
        });
    }

    @Override
    public boolean checkHealth(UUID equipmentId) {
        return true; // Simulator is always healthy
    }

    // --- Error Injection API ---

    public void injectError(UUID equipmentId, int errorCode) {
        injectedErrors.put(equipmentId, errorCode);
        log.info("[SIM] Error injection set: equipment={} code={}", equipmentId, errorCode);
    }

    public void clearErrors() {
        injectedErrors.clear();
        log.info("[SIM] All error injections cleared");
    }

    public void setDelay(UUID equipmentId, long delayMs) {
        injectedDelays.put(equipmentId, delayMs);
    }

    public void setDefaultDelay(long delayMs) {
        this.defaultDelayMs = delayMs;
    }
}
