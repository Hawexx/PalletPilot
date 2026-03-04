package com.palletpilot.wcs.infrastructure.beckhoff;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.palletpilot.wcs.domain.model.Equipment;
import com.palletpilot.wcs.domain.model.EquipmentCapabilities;
import com.palletpilot.wcs.domain.port.out.HardwareClient;
import com.palletpilot.wcs.application.service.AlertService;
import com.palletpilot.wcs.application.service.PlcSeqIdService;
import com.palletpilot.wcs.application.service.SystemStateService;
import com.palletpilot.wcs.domain.model.enums.SystemState;
import com.palletpilot.wcs.infrastructure.persistence.repository.EquipmentRepository;
import com.palletpilot.wcs.application.service.ConfigurationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.plc4x.java.PlcDriverManager;
import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.api.messages.PlcReadResponse;
import org.apache.plc4x.java.api.messages.PlcWriteResponse;
import org.apache.plc4x.java.api.types.PlcResponseCode;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Production-grade Beckhoff ADS client using Apache PLC4X.
 * Implements the PLC/TwinCAT ICD v1.0 (see docs/PLC_TWINCAT_ICD.md):
 * <ul>
 * <li>6-state handshake (Idle/Busy/Done/Error/EStop/Interlock)</li>
 * <li>DB-backed monotonic SeqId via {@link PlcSeqIdService}</li>
 * <li>Clears Command.Valid=false after observing
 * DONE/ERROR/E-STOP/INTERLOCK/TIMEOUT</li>
 * <li>Reads ProgressPct and LastCmd from Status struct</li>
 * <li>E-Stop → automatic system EMERGENCY_STOP transition</li>
 * <li>Interlock → CRITICAL alert with actionable message (equipment-local)</li>
 * </ul>
 */
@Component("beckhoffClient")
@RequiredArgsConstructor
@Slf4j
public class BeckhoffAdsClient implements HardwareClient {

    private final EquipmentRepository equipmentRepository;
    private final ObjectMapper objectMapper;
    private final ConfigurationService configService;
    private final AlertService alertService;
    private final SystemStateService systemStateService;
    private final PlcSeqIdService plcSeqIdService;

    // ICD State constants (see docs/PLC_TWINCAT_ICD.md §3)
    public static final int STATE_IDLE = 0;
    public static final int STATE_BUSY = 1;
    public static final int STATE_DONE = 2;
    public static final int STATE_ERROR = 3;
    public static final int STATE_ESTOP = 4;
    public static final int STATE_INTERLOCK = 5;

    // Configurable defaults
    private static final int DEFAULT_TIMEOUT_MS = 5000;
    private static final int DEFAULT_POLL_INTERVAL_MS = 200;
    private static final int DEFAULT_RETRIES = 1;

    @Override
    public CompletableFuture<Boolean> sendCommand(UUID equipmentId, String commandString) {
        final String correlationId = UUID.randomUUID().toString().substring(0, 8);

        return CompletableFuture.supplyAsync(() -> {
            try {
                Equipment eq = equipmentRepository.findById(equipmentId)
                        .orElseThrow(() -> new IllegalArgumentException("Equipment not found: " + equipmentId));

                EquipmentCapabilities caps = parseCapabilities(eq);
                int timeoutMs = caps.getEffectiveTimeoutMs(DEFAULT_TIMEOUT_MS);
                int pollMs = caps.getEffectivePollIntervalMs(DEFAULT_POLL_INTERVAL_MS);

                log.info("[ADS] cid={} equipment={} cmd={} timeout={}ms",
                        correlationId, eq.getName(), commandString, timeoutMs);

                return executeWithRetry(eq, commandString, timeoutMs, pollMs, DEFAULT_RETRIES, correlationId);

            } catch (Exception e) {
                log.error("[ADS] cid={} equipment={} FATAL: {}",
                        correlationId, equipmentId, e.getMessage());
                return false;
            }
        });
    }

    private boolean executeWithRetry(Equipment eq, String commandString,
            int timeoutMs, int pollMs, int retries, String correlationId) {
        for (int attempt = 0; attempt <= retries; attempt++) {
            try {
                return executeHandshake(eq, commandString, timeoutMs, pollMs, correlationId);
            } catch (Exception e) {
                log.warn("[ADS] cid={} equipment={} attempt={}/{} error={}",
                        correlationId, eq.getName(), attempt + 1, retries + 1, e.getMessage());
                if (attempt == retries) {
                    return false;
                }
            }
        }
        return false;
    }

    /**
     * Execute the full ICD v1.0 handshake:
     * 1. Write Command (Cmd + SeqId + Valid) atomically
     * 2. Poll Status until Done/Error/EStop/Interlock/Timeout
     * 3. Clear Command.Valid=false after terminal state
     */
    private boolean executeHandshake(Equipment eq, String commandString,
            int timeoutMs, int pollMs, String correlationId) throws Exception {
        String connectionString = buildConnectionString(eq);
        // DB-backed monotonic SeqId — survives JVM restarts
        long seqId = plcSeqIdService.nextSeqId(eq.getId());

        EquipmentCapabilities caps = parseCapabilities(eq);
        String symbolPath = caps.getSymbolPath() != null ? caps.getSymbolPath() : "MAIN";

        log.debug("[ADS] cid={} symbol={} seqId={}", correlationId, symbolPath, seqId);

        try (PlcConnection plcConnection = new PlcDriverManager().getConnection(connectionString)) {
            if (!plcConnection.isConnected()) {
                throw new IllegalStateException("Failed to connect to ADS: " + connectionString);
            }

            // === Step 1: Write Command atomically ===
            String cmdPath = symbolPath + ".Command";

            PlcWriteResponse writeResponse = plcConnection.writeRequestBuilder()
                    .addItem("cmd", cmdPath + ".Cmd", commandString)
                    .addItem("seqId", cmdPath + ".SeqId", seqId)
                    .addItem("valid", cmdPath + ".Valid", true)
                    .build()
                    .execute()
                    .get(2000, TimeUnit.MILLISECONDS);

            if (writeResponse.getResponseCode("cmd") != PlcResponseCode.OK) {
                throw new IllegalStateException("PLC write rejected: " +
                        writeResponse.getResponseCode("cmd"));
            }

            log.info("[ADS] cid={} equipment={} seqId={} SENT",
                    correlationId, eq.getName(), seqId);

            // === Step 2: Poll Status ===
            long deadline = System.currentTimeMillis() + timeoutMs;
            String statusPath = symbolPath + ".Status";
            try {
                while (System.currentTimeMillis() < deadline) {
                    PlcReadResponse readResponse = plcConnection.readRequestBuilder()
                            .addItem("ackSeqId", statusPath + ".AckSeqId")
                            .addItem("state", statusPath + ".State")
                            .addItem("errorCode", statusPath + ".ErrorCode")
                            .addItem("errorText", statusPath + ".ErrorText")
                            .addItem("progressPct", statusPath + ".ProgressPct")
                            .addItem("lastCmd", statusPath + ".LastCmd")
                            .build()
                            .execute()
                            .get(1000, TimeUnit.MILLISECONDS);

                    if (readResponse.getResponseCode("ackSeqId") == PlcResponseCode.OK) {
                        Long ackSeqId = readResponse.getLong("ackSeqId");
                        Integer state = readResponse.getInteger("state");
                        Integer errorCode = readResponse.getInteger("errorCode");
                        String errorText = readResponse.getString("errorText");
                        Integer progressPct = readResponse.getResponseCode("progressPct") == PlcResponseCode.OK
                                ? readResponse.getInteger("progressPct")
                                : null;

                        // --- Safety checks (any state, regardless of SeqId) ---
                        if (state != null && state == STATE_ESTOP) {
                            handleEStop(eq, correlationId, errorText);
                            return false; // clearValid in finally
                        }

                        if (state != null && state == STATE_INTERLOCK) {
                            handleInterlock(eq, correlationId, errorText);
                            return false; // clearValid in finally
                        }

                        // --- Command acknowledgment checks ---
                        if (ackSeqId != null && ackSeqId == seqId) {
                            if (state == STATE_DONE) {
                                log.info("[ADS] cid={} equipment={} seqId={} DONE",
                                        correlationId, eq.getName(), seqId);
                                return true; // clearValid in finally
                            }

                            if (state == STATE_ERROR) {
                                log.error("[ADS] cid={} equipment={} seqId={} ERROR code={} text={}",
                                        correlationId, eq.getName(), seqId,
                                        errorCode != null ? errorCode : 0,
                                        errorText != null ? errorText : "unknown");

                                alertService.raiseAlert(eq.getId(),
                                        String.format("PLC Error on %s: [%d] %s (cid=%s)",
                                                eq.getName(),
                                                errorCode != null ? errorCode : 0,
                                                errorText != null ? errorText : "unknown",
                                                correlationId),
                                        "CRITICAL");
                                return false; // clearValid in finally
                            }

                            // STATE_BUSY → log progress and continue polling
                            if (state == STATE_BUSY && progressPct != null) {
                                log.debug("[ADS] cid={} equipment={} seqId={} BUSY progress={}%",
                                        correlationId, eq.getName(), seqId, progressPct);
                            }
                        }
                    }

                    Thread.sleep(pollMs);
                }

                // Timeout
                log.error("[ADS] cid={} equipment={} seqId={} TIMEOUT after {}ms",
                        correlationId, eq.getName(), seqId, timeoutMs);
                alertService.raiseAlert(eq.getId(),
                        String.format("PLC command timeout on %s after %dms (cid=%s, seqId=%d). " +
                                "Check TwinCAT runtime and network connectivity.",
                                eq.getName(), timeoutMs, correlationId, seqId),
                        "WARNING");
                return false; // clearValid in finally
            } finally {
                // === Step 3: ICD requirement — WCS clears Valid=false after terminal state ===
                clearValid(plcConnection, cmdPath, correlationId, eq.getName());
            }
        }
    }

    /**
     * Clear Command.Valid=false per ICD v1.0 design fix:
     * PLC NEVER writes to Command struct. WCS clears Valid after
     * DONE/ERROR/TIMEOUT.
     */
    private void clearValid(PlcConnection plcConnection, String cmdPath,
            String correlationId, String equipmentName) {
        try {
            PlcWriteResponse clearResponse = plcConnection.writeRequestBuilder()
                    .addItem("valid", cmdPath + ".Valid", false)
                    .build()
                    .execute()
                    .get(1000, TimeUnit.MILLISECONDS);

            if (clearResponse.getResponseCode("valid") == PlcResponseCode.OK) {
                log.debug("[ADS] cid={} equipment={} Valid=false CLEARED", correlationId, equipmentName);
            } else {
                log.warn("[ADS] cid={} equipment={} Failed to clear Valid: {}",
                        correlationId, equipmentName, clearResponse.getResponseCode("valid"));
            }
        } catch (Exception e) {
            // Best-effort: don't fail the whole command if clear fails
            log.warn("[ADS] cid={} equipment={} clearValid error (best-effort): {}",
                    correlationId, equipmentName, e.getMessage());
        }
    }

    /**
     * Handle E-Stop detection from PLC.
     * Transitions entire system to EMERGENCY_STOP.
     */
    private void handleEStop(Equipment eq, String correlationId, String errorText) {
        String reason = String.format("Hardware E-Stop detected on %s: %s (cid=%s)",
                eq.getName(), errorText != null ? errorText : "E-Stop engaged", correlationId);

        log.error("[ADS] cid={} equipment={} EMERGENCY_STOP: {}", correlationId, eq.getName(), reason);

        alertService.raiseAlert(eq.getId(), reason, "CRITICAL");

        try {
            if (!systemStateService.isEmergencyStop()) {
                systemStateService.transitionTo(SystemState.EMERGENCY_STOP, "PLC", reason);
            }
        } catch (Exception e) {
            log.error("[ADS] cid={} Failed to transition system to E-STOP: {}", correlationId, e.getMessage());
        }
    }

    /**
     * Handle Interlock detection from PLC.
     * Raises CRITICAL alert but does NOT stop the whole system (equipment-local).
     */
    private void handleInterlock(Equipment eq, String correlationId, String errorText) {
        String reason = String.format("Safety interlock active on %s: %s. Check guard/door. (cid=%s)",
                eq.getName(), errorText != null ? errorText : "Interlock active", correlationId);

        log.error("[ADS] cid={} equipment={} INTERLOCK: {}", correlationId, eq.getName(), reason);

        alertService.raiseAlert(eq.getId(), reason, "CRITICAL");
    }

    /**
     * Parse EquipmentCapabilities from Equipment.configJson.
     * Returns empty capabilities on parse failure (permissive default).
     */
    EquipmentCapabilities parseCapabilities(Equipment eq) {
        if (eq.getConfigJson() == null || eq.getConfigJson().isBlank()) {
            return new EquipmentCapabilities();
        }
        try {
            return objectMapper.readValue(eq.getConfigJson(), EquipmentCapabilities.class);
        } catch (Exception e) {
            log.warn("[ADS] Failed to parse capabilities for {}: {}", eq.getName(), e.getMessage());
            return new EquipmentCapabilities();
        }
    }

    @Override
    public boolean checkHealth(UUID equipmentId) {
        try {
            Equipment eq = equipmentRepository.findById(equipmentId).orElse(null);
            if (eq == null)
                return false;

            String connectionString = buildConnectionString(eq);
            try (PlcConnection plcConnection = new PlcDriverManager().getConnection(connectionString)) {
                return plcConnection.isConnected();
            }
        } catch (Exception e) {
            return false;
        }
    }

    private String buildConnectionString(Equipment eq) {
        EquipmentCapabilities caps = parseCapabilities(eq);
        String targetAmsId = caps.getAdsNetId();

        if (targetAmsId == null || targetAmsId.isEmpty()) {
            throw new IllegalArgumentException("Missing ADS Net ID in configuration for " + eq.getName());
        }

        String sourceAmsNetId = configService.getString("hardware.beckhoff.local-ams-net-id", "192.168.1.10.1.1");

        return String.format("ads:tcp://%s?targetAmsNetId=%s&sourceAmsNetId=%s",
                eq.getIpAddress(), targetAmsId, sourceAmsNetId);
    }
}
