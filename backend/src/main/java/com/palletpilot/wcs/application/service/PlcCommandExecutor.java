package com.palletpilot.wcs.application.service;

import com.palletpilot.wcs.application.metrics.PlcMetrics;
import com.palletpilot.wcs.domain.model.CommandLog;
import com.palletpilot.wcs.domain.model.Equipment;

import com.palletpilot.wcs.domain.model.enums.CommandStatus;
import com.palletpilot.wcs.domain.model.enums.CommandType;

import com.palletpilot.wcs.infrastructure.beckhoff.BeckhoffAdsClient;
import com.palletpilot.wcs.infrastructure.beckhoff.PlcCommandFormatter;
import com.palletpilot.wcs.infrastructure.persistence.repository.CommandLogRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.EquipmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * Production-grade PLC command executor with:
 * <ul>
 * <li>Per-equipment single-command locking (no parallel commands for same
 * equipment)</li>
 * <li>Safety precheck (EMERGENCY_STOP blocks all new commands)</li>
 * <li>Non-idempotent command protection (no auto-retry for PICK/WRAP)</li>
 * <li>Micrometer metrics at each state transition</li>
 * <li>CommandLog persistence at every step</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlcCommandExecutor {

    private final BeckhoffAdsClient adsClient;
    private final PlcCommandFormatter commandFormatter;
    private final PlcMetrics plcMetrics;
    private final SystemStateService systemStateService;
    private final CommandLogRepository commandLogRepository;
    private final EquipmentRepository equipmentRepository;
    private final PlcSeqIdService plcSeqIdService;

    // Per-equipment semaphore: max 1 concurrent command per equipment
    private final ConcurrentHashMap<UUID, Semaphore> equipmentLocks = new ConcurrentHashMap<>();

    /**
     * Execute a PLC command with full safety, locking, and observability.
     *
     * @param equipmentId target equipment UUID
     * @param commandType the command type enum
     * @param params      optional parameters (e.g., SPEED=1500)
     * @param businessKey correlation key (e.g., palletId) for traceability
     * @return true if command completed with DONE, false otherwise
     */
    public boolean execute(UUID equipmentId, CommandType commandType,
            Map<String, String> params, String businessKey) {
        String correlationId = UUID.randomUUID().toString().substring(0, 8);

        // 1. Safety precheck
        if (systemStateService.isEmergencyStop()) {
            log.warn("[PLC-EXEC] cid={} REJECTED: System in EMERGENCY_STOP", correlationId);
            return false;
        }

        // 2. Look up equipment
        Equipment eq = equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Equipment not found: " + equipmentId));

        // 3. Format command string
        String cmdString = commandFormatter.format(commandType, params);

        // 4. Create CommandLog entry
        CommandLog cmdLog = CommandLog.builder()
                .equipmentId(equipmentId)
                .commandPayload(cmdString)
                .status(CommandStatus.QUEUED)
                .commandType(commandType.name())
                .correlationId(correlationId)
                .idempotencyKey(correlationId + ":" + commandType + ":" + System.currentTimeMillis())
                .build();
        cmdLog = commandLogRepository.save(cmdLog);

        // 5. Acquire per-equipment lock
        Semaphore lock = equipmentLocks.computeIfAbsent(equipmentId, k -> new Semaphore(1));

        try {
            if (!lock.tryAcquire()) {
                log.warn("[PLC-EXEC] cid={} equipment={} REJECTED: Another command in progress",
                        correlationId, eq.getName());
                cmdLog.setStatus(CommandStatus.FAILED);
                cmdLog.setErrorText("Equipment busy — another command in progress");
                commandLogRepository.save(cmdLog);
                return false;
            }

            // 6. Mark as SENT
            long startTime = System.currentTimeMillis();
            cmdLog.setStatus(CommandStatus.SENT);
            commandLogRepository.save(cmdLog);
            plcMetrics.recordSent();

            log.info("[PLC-EXEC] cid={} equipment={} cmd={} type={} businessKey={}",
                    correlationId, eq.getName(), cmdString, commandType, businessKey);

            // 7. Delegate to BeckhoffAdsClient for actual ADS handshake
            boolean success = adsClient.sendCommand(equipmentId, cmdString)
                    .get(120, java.util.concurrent.TimeUnit.SECONDS);

            long durationMs = System.currentTimeMillis() - startTime;

            // 8. Update CommandLog with result
            if (success) {
                cmdLog.setStatus(CommandStatus.COMPLETED);
                cmdLog.setCompletedAt(Instant.now());
                plcMetrics.recordDone();
                plcMetrics.recordDuration(eq.getType().name(), commandType.name(), durationMs);
                log.info("[PLC-EXEC] cid={} equipment={} COMPLETED in {}ms",
                        correlationId, eq.getName(), durationMs);
            } else {
                cmdLog.setStatus(CommandStatus.FAILED);
                cmdLog.setCompletedAt(Instant.now());

                // Determine failure type from system state
                if (systemStateService.isEmergencyStop()) {
                    plcMetrics.recordEstop();
                    cmdLog.setErrorText("Command aborted by EMERGENCY_STOP");
                } else {
                    plcMetrics.recordError();
                    cmdLog.setErrorText("PLC returned ERROR or TIMEOUT");
                }

                log.warn("[PLC-EXEC] cid={} equipment={} FAILED in {}ms",
                        correlationId, eq.getName(), durationMs);

                // Non-idempotent command warning
                if (!commandType.isIdempotent()) {
                    log.warn("[PLC-EXEC] cid={} Non-idempotent command {} — DO NOT auto-retry",
                            correlationId, commandType);
                }
            }

            commandLogRepository.save(cmdLog);
            return success;

        } catch (java.util.concurrent.TimeoutException e) {
            cmdLog.setStatus(CommandStatus.FAILED);
            cmdLog.setErrorText("Executor timeout (120s)");
            cmdLog.setCompletedAt(Instant.now());
            commandLogRepository.save(cmdLog);
            plcMetrics.recordTimeout();
            log.error("[PLC-EXEC] cid={} equipment={} EXECUTOR TIMEOUT", correlationId, eq.getName());
            return false;

        } catch (Exception e) {
            cmdLog.setStatus(CommandStatus.FAILED);
            cmdLog.setErrorText("Unexpected: " + e.getMessage());
            cmdLog.setCompletedAt(Instant.now());
            commandLogRepository.save(cmdLog);
            plcMetrics.recordError();
            log.error("[PLC-EXEC] cid={} equipment={} UNEXPECTED: {}",
                    correlationId, eq.getName(), e.getMessage(), e);
            return false;

        } finally {
            lock.release();
        }
    }
}
