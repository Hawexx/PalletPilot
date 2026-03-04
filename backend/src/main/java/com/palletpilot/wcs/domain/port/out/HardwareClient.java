package com.palletpilot.wcs.domain.port.out;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface HardwareClient {
    /**
     * Sends a command to the physical (or simulated) hardware.
     * 
     * @param equipmentId Target ID
     * @param command     Command String/JSON
     * @return CompletableFuture that completes when command is ACKNOWLEDGED (not
     *         finished)
     */
    CompletableFuture<Boolean> sendCommand(UUID equipmentId, String command);

    /**
     * Checks liveness.
     */
    boolean checkHealth(UUID equipmentId);
}
