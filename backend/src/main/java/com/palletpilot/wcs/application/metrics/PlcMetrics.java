package com.palletpilot.wcs.application.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Micrometer metrics for PLC command execution.
 * <p>
 * Counters:
 * -
 * palletpilot_plc_commands_total{result=sent|done|error|timeout|estop|interlock}
 * <p>
 * Timers:
 * - palletpilot_plc_command_duration_ms{equipmentType, action}
 */
@Component
public class PlcMetrics {

    private final MeterRegistry registry;

    // Pre-built counters for hot path
    private final Counter sentCounter;
    private final Counter doneCounter;
    private final Counter errorCounter;
    private final Counter timeoutCounter;
    private final Counter estopCounter;
    private final Counter interlockCounter;

    public PlcMetrics(MeterRegistry registry) {
        this.registry = registry;

        this.sentCounter = Counter.builder("palletpilot_plc_commands_total")
                .tag("result", "sent")
                .description("PLC commands sent to TwinCAT")
                .register(registry);

        this.doneCounter = Counter.builder("palletpilot_plc_commands_total")
                .tag("result", "done")
                .description("PLC commands completed successfully")
                .register(registry);

        this.errorCounter = Counter.builder("palletpilot_plc_commands_total")
                .tag("result", "error")
                .description("PLC commands that returned ERROR")
                .register(registry);

        this.timeoutCounter = Counter.builder("palletpilot_plc_commands_total")
                .tag("result", "timeout")
                .description("PLC commands that timed out")
                .register(registry);

        this.estopCounter = Counter.builder("palletpilot_plc_commands_total")
                .tag("result", "estop")
                .description("PLC commands aborted by E-STOP")
                .register(registry);

        this.interlockCounter = Counter.builder("palletpilot_plc_commands_total")
                .tag("result", "interlock")
                .description("PLC commands rejected by INTERLOCK")
                .register(registry);
    }

    public void recordSent() {
        sentCounter.increment();
    }

    public void recordDone() {
        doneCounter.increment();
    }

    public void recordError() {
        errorCounter.increment();
    }

    public void recordTimeout() {
        timeoutCounter.increment();
    }

    public void recordEstop() {
        estopCounter.increment();
    }

    public void recordInterlock() {
        interlockCounter.increment();
    }

    /**
     * Record command execution duration for a specific equipment type and action.
     *
     * @param equipmentType e.g., "KUKA", "CONVEYOR", "WRAPPER"
     * @param action        e.g., "PICK", "START", "WRAP"
     * @param durationMs    execution time in milliseconds
     */
    public void recordDuration(String equipmentType, String action, long durationMs) {
        Timer.builder("palletpilot_plc_command_duration_ms")
                .tag("equipmentType", equipmentType)
                .tag("action", action)
                .description("PLC command execution duration")
                .register(registry)
                .record(Duration.ofMillis(durationMs));
    }
}
