package com.palletpilot.wcs.infrastructure.simulation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for SimulatedHardwareClient — simulation mode hardware.
 * Tests: sendCommand returns true, checkHealth always true, async behavior.
 */
class SimulatedHardwareClientTest {

    private final SimulatedHardwareClient client = new SimulatedHardwareClient();

    @Test
    @DisplayName("checkHealth: always returns true in simulation")
    void checkHealth_alwaysTrue() {
        boolean result = client.checkHealth(UUID.randomUUID());
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("sendCommand: returns CompletableFuture that resolves to true")
    void sendCommand_resolvesTrue() throws Exception {
        CompletableFuture<Boolean> future = client.sendCommand(UUID.randomUUID(), "PICK");

        // Should complete within 2 seconds (500ms simulated delay + buffer)
        Boolean result = future.get(2, TimeUnit.SECONDS);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("sendCommand: returns non-null CompletableFuture")
    void sendCommand_returnsNonNull() {
        CompletableFuture<Boolean> future = client.sendCommand(UUID.randomUUID(), "MOVE");
        assertThat(future).isNotNull();
    }
}
