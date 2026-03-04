package com.palletpilot.wcs.infrastructure.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Getter;
import org.springframework.stereotype.Component;

/**
 * Custom business metrics for PalletPilot monitoring.
 *
 * Metrics exposed at /actuator/prometheus:
 * palletpilot_orders_created_total
 * palletpilot_commands_executed_total
 * palletpilot_commands_failed_total
 * palletpilot_state_changes_total
 */
@Component
@Getter
public class MetricsService {

    private final Counter ordersCreated;
    private final Counter commandsExecuted;
    private final Counter commandsFailed;
    private final Counter stateChanges;

    public MetricsService(MeterRegistry registry) {
        this.ordersCreated = Counter.builder("palletpilot.orders.created")
                .description("Total WCS orders created")
                .register(registry);

        this.commandsExecuted = Counter.builder("palletpilot.commands.executed")
                .description("Total equipment commands executed")
                .register(registry);

        this.commandsFailed = Counter.builder("palletpilot.commands.failed")
                .description("Total equipment commands that failed")
                .register(registry);

        this.stateChanges = Counter.builder("palletpilot.state.changes")
                .description("Total system state transitions")
                .register(registry);
    }

    public void incrementOrdersCreated() {
        ordersCreated.increment();
    }

    public void incrementCommandsExecuted() {
        commandsExecuted.increment();
    }

    public void incrementCommandsFailed() {
        commandsFailed.increment();
    }

    public void incrementStateChanges() {
        stateChanges.increment();
    }
}
