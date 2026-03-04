package com.palletpilot.wcs.application.service;

import com.palletpilot.wcs.domain.model.CommandLog;
import com.palletpilot.wcs.domain.model.enums.CommandStatus;
import com.palletpilot.wcs.domain.model.enums.CommandType;
import com.palletpilot.wcs.infrastructure.persistence.repository.CommandLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Async command worker that polls for QUEUED PLC commands
 * and dispatches them to {@link PlcCommandExecutor}.
 * <p>
 * Enabled only when {@code hardware.plc.enabled=true}.
 * Uses a fixed-delay poll to pick queued commands ordered by creation time.
 * Only one command per equipment is processed at a time (enforced by executor).
 * </p>
 */
@Component
@ConditionalOnProperty(name = "hardware.plc.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class PlcCommandWorker {

    private final CommandLogRepository commandLogRepository;
    private final PlcCommandExecutor plcCommandExecutor;

    @Value("${hardware.plc.max-inflight-per-equipment:1}")
    private int maxInflightPerEquipment;

    /**
     * Poll for queued PLC commands and execute them.
     * Runs on a fixed delay (default 1000ms, configurable).
     */
    @Scheduled(fixedDelayString = "${hardware.plc.worker-poll-ms:1000}")
    public void processQueuedCommands() {
        try {
            List<CommandLog> queued = commandLogRepository.findByStatusOrderByCreatedAtAsc(CommandStatus.QUEUED);

            if (queued.isEmpty()) {
                return;
            }

            log.debug("[PLC-WORKER] Found {} queued command(s)", queued.size());

            // Track which equipment already has an in-flight command this cycle
            java.util.Set<UUID> processedEquipment = new java.util.HashSet<>();

            for (CommandLog cmd : queued) {
                UUID equipmentId = cmd.getEquipmentId();

                // Skip if we already dispatched a command for this equipment
                if (processedEquipment.contains(equipmentId)) {
                    continue;
                }

                processedEquipment.add(equipmentId);

                try {
                    // Parse command type (may be null for legacy commands)
                    CommandType type = null;
                    if (cmd.getCommandType() != null) {
                        try {
                            type = CommandType.valueOf(cmd.getCommandType());
                        } catch (IllegalArgumentException e) {
                            log.warn("[PLC-WORKER] Unknown command type: {}, using PLC_GENERIC",
                                    cmd.getCommandType());
                            type = CommandType.PLC_GENERIC;
                        }
                    } else {
                        type = CommandType.PLC_GENERIC;
                    }

                    log.info("[PLC-WORKER] Dispatching queued command: id={} equipment={} type={}",
                            cmd.getId(), equipmentId, type);

                    // Update status to indicate we're processing
                    cmd.setStatus(CommandStatus.SENT);
                    commandLogRepository.save(cmd);

                    // Execute via the PlcCommandExecutor (blocking)
                    boolean success = plcCommandExecutor.execute(
                            equipmentId,
                            type,
                            Map.of(), // params already encoded in cmd.getCommand()
                            cmd.getCorrelationId() != null ? cmd.getCorrelationId() : "");

                    if (success) {
                        log.info("[PLC-WORKER] Command completed: id={}", cmd.getId());
                    } else {
                        log.warn("[PLC-WORKER] Command failed: id={}", cmd.getId());
                    }

                } catch (Exception e) {
                    log.error("[PLC-WORKER] Error processing command id={}: {}",
                            cmd.getId(), e.getMessage(), e);
                    cmd.setStatus(CommandStatus.FAILED);
                    cmd.setErrorText("Worker error: " + e.getMessage());
                    commandLogRepository.save(cmd);
                }
            }

        } catch (Exception e) {
            log.error("[PLC-WORKER] Unhandled error in worker cycle: {}", e.getMessage(), e);
        }
    }
}
