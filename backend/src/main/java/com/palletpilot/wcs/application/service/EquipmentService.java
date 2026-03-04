package com.palletpilot.wcs.application.service;

import com.palletpilot.wcs.domain.model.Equipment;
import com.palletpilot.wcs.domain.model.enums.EquipmentState;
import com.palletpilot.wcs.domain.port.out.HardwareClient;
import com.palletpilot.wcs.infrastructure.persistence.repository.EquipmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.palletpilot.wcs.domain.model.CommandLog;
import com.palletpilot.wcs.domain.model.enums.CommandStatus;
import com.palletpilot.wcs.infrastructure.persistence.repository.CommandLogRepository;
import com.palletpilot.wcs.infrastructure.license.LicenseGuard;

@Service
@Slf4j
@RequiredArgsConstructor
public class EquipmentService {

    private final EquipmentRepository equipmentRepository;
    private final AlertService alertService;
    private final Map<String, HardwareClient> clients; // Injected by Spring: "movuClient", "kukaClient", etc.
    private final ConfigurationService configService;
    private final SystemStateService systemStateService;
    private final CommandLogRepository commandLogRepository;
    private final AuditService auditService;

    // @Value("${app.simulation.enabled:true}")
    // private boolean simulationEnabled;

    /**
     * Executes a command on the target equipment.
     * Returns the Command ID for tracking.
     */
    @Transactional
    public String executeCommand(UUID equipmentId, String commandPayload) {
        LicenseGuard.assertRuntimeValidity();
        log.info("Executing command for Equipment ID: {}", equipmentId);

        // 0. SAFETY CHECK
        if (!systemStateService.isRunning()) {
            String msg = "SAFETY BLOCK: Command rejected. System State is " + systemStateService.getCurrentState();
            log.warn("BLOCKED: {} -> {}", equipmentId, commandPayload);
            throw new IllegalStateException(msg);
        }

        // 1. Load Equipment
        Equipment equipment = equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Equipment not found: " + equipmentId));

        // 2. Validate State
        if (equipment.getStatus() == EquipmentState.BUSY || equipment.getStatus() == EquipmentState.OFFLINE) {
            String msg = "Cannot execute command. Equipment " + equipment.getName() + " is " + equipment.getStatus();
            alertService.raiseAlert(equipmentId, msg, "WARNING");
            throw new IllegalStateException(msg);
        }

        // 3. PERSIST COMMAND LOG
        CommandLog commandLog = CommandLog.builder()
                .equipmentId(equipmentId)
                .commandPayload(commandPayload)
                .status(CommandStatus.SENT)
                .build();
        commandLogRepository.save(commandLog);

        // 4. Update State to BUSY
        equipment.setStatus(EquipmentState.BUSY);
        equipmentRepository.save(equipment);

        // 5. Select Client
        HardwareClient client = selectClient(equipment);

        // 6. Send Command Async
        client.sendCommand(equipmentId, commandPayload)
                .thenAccept(success -> handleCommandResult(commandLog.getId(), equipmentId, success));

        auditService.log("COMMAND_EXECUTED", "Equipment", equipmentId.toString(), null,
                String.format("{\"commandPayload\":\"%s\",\"equipmentName\":\"%s\"}",
                        commandPayload, equipment.getName()));

        return commandLog.getId();
    }

    private HardwareClient selectClient(Equipment equipment) {
        boolean simulationEnabled = configService.getBoolean("app.simulation.enabled", false);
        if (simulationEnabled) {
            return clients.get("simulationClient");
        }

        // 1. Route by Explicit Protocol
        if (equipment.getProtocol() != null) {
            switch (equipment.getProtocol().toUpperCase()) {
                case "ADS":
                case "BECKHOFF":
                    return clients.get("beckhoffClient");
                case "MODBUS":
                case "MODBUS-TCP":
                    return clients.get("modbusClient");
                case "S7":
                case "SIEMENS":
                case "PROFINET":
                    return clients.get("siemensClient");
                case "HTTP":
                case "REST":
                    return clients.get("movuClient");
            }
        }

        // 2. Route by Equipment Type (Legacy/Default)
        return switch (equipment.getType()) {
            case MOVU -> clients.get("movuClient");
            case KUKA, CONVEYOR, CYKLOP_WRAPPER, BECKHOFF_PLC -> clients.get("beckhoffClient");
            case SIEMENS_PLC -> clients.get("siemensClient");
            case AMR_ROBOT -> clients.get("simulationClient"); // Placeholder
            case CUSTOM -> clients.get("modbusClient"); // Default custom to Modbus? Or Sim? Let's default to Sim for
                                                        // safety.
            default -> clients.get("simulationClient");
        };
    }

    @Transactional
    protected void handleCommandResult(String commandId, UUID equipmentId, boolean success) {
        // Update Command Log
        commandLogRepository.findById(commandId).ifPresent(logEntry -> {
            logEntry.setStatus(success ? CommandStatus.ACKED
                    : CommandStatus.FAILED);
            if (!success) {
                logEntry.setFailureReason("Hardware Client rejected command");
            }
            commandLogRepository.save(logEntry);
        });

        // Reload to avoid stale state in async context
        equipmentRepository.findById(equipmentId).ifPresent(eq -> {
            if (success) {
                log.info("Command {} acknowledged by {}", commandId, eq.getName());
                // Note: Stays BUSY until Callback or Completion Signal
            } else {
                log.error("Command {} failed for {}", commandId, eq.getName());
                eq.setStatus(EquipmentState.ERROR);
                equipmentRepository.save(eq);
                alertService.raiseAlert(equipmentId, "Command execution failed for " + eq.getName(), "CRITICAL");
            }
        });
    }

    @Transactional
    public void onWorkCompletedCallback(UUID equipmentId) {
        log.info("Received completion callback for {}", equipmentId);

        // Mark active commands as COMPLETED?
        // KNOWN LIMITATION: PLC callbacks don't include CommandID yet.
        // Using heuristic: mark latest ACKED command as COMPLETED on callback.
        // RISK: If multiple commands are in-flight, wrong command may be marked.
        // FIX: Update PLC communication protocol to echo CommandID in completion
        // callback.
        log.warn("[EQUIPMENT] Completion callback for {} uses heuristic command matching — " +
                "PLC protocol should return CommandID for production safety", equipmentId);

        List<CommandLog> activeLogs = commandLogRepository
                .findByEquipmentIdAndStatus(equipmentId, CommandStatus.ACKED);
        for (CommandLog log : activeLogs) {
            log.setStatus(CommandStatus.COMPLETED);
            commandLogRepository.save(log);
        }

        equipmentRepository.findById(equipmentId).ifPresent(eq -> {
            eq.setStatus(EquipmentState.IDLE); // Back to IDLE
            eq.setLastHeartbeat(Instant.now());
            equipmentRepository.save(eq);
            alertService.raiseAlert(equipmentId, "Work completed for " + eq.getName(), "INFO");
        });
    }

    // CRUD Methods
    @Cacheable("equipmentList")
    public Iterable<Equipment> getAllEquipment() {
        return equipmentRepository.findAll();
    }

    public Equipment getEquipment(UUID id) {
        return equipmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Equipment not found: " + id));
    }

    @Transactional
    @CacheEvict(value = "equipmentList", allEntries = true)
    public Equipment createEquipment(Equipment equipment) {
        if (equipmentRepository.existsByName(equipment.getName())) {
            throw new IllegalArgumentException("Equipment with name " + equipment.getName() + " already exists");
        }
        equipment.setStatus(EquipmentState.IDLE);
        Equipment saved = equipmentRepository.save(equipment);

        auditService.log("EQUIPMENT_REGISTERED", "Equipment", saved.getId().toString(), null,
                String.format("{\"name\":\"%s\",\"type\":\"%s\"}", saved.getName(), saved.getType()));

        return saved;
    }

    @Transactional
    public Equipment updateEquipment(UUID id, Equipment updates) {
        Equipment existing = getEquipment(id);
        if (updates.getName() != null)
            existing.setName(updates.getName());
        if (updates.getIpAddress() != null)
            existing.setIpAddress(updates.getIpAddress());
        if (updates.getPort() != null)
            existing.setPort(updates.getPort());
        if (updates.getProtocol() != null)
            existing.setProtocol(updates.getProtocol());
        if (updates.getApiKey() != null)
            existing.setApiKey(updates.getApiKey());
        if (updates.getType() != null)
            existing.setType(updates.getType());
        return equipmentRepository.save(existing);
    }

    @Transactional
    public void deleteEquipment(UUID id) {
        if (!equipmentRepository.existsById(id)) {
            throw new IllegalArgumentException("Equipment not found");
        }
        equipmentRepository.deleteById(id);
    }

    @Transactional
    public boolean testConnection(UUID id) {
        Equipment eq = getEquipment(id);
        if (eq.getIpAddress() == null || eq.getPort() == null) {
            log.warn("Cannot test connection for {} - missing IP/Port", eq.getName());
            return false;
        }

        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress(eq.getIpAddress(), eq.getPort()), 2000); // 2s timeout

            if (eq.getStatus() != EquipmentState.BUSY) {
                eq.setStatus(EquipmentState.IDLE);
                eq.setLastHeartbeat(Instant.now());
                equipmentRepository.save(eq);
            }
            return true;
        } catch (Exception e) {
            log.warn("Connection test failed for {}: {}", eq.getName(), e.getMessage());
            if (eq.getStatus() != EquipmentState.ERROR) {
                eq.setStatus(EquipmentState.OFFLINE);
                equipmentRepository.save(eq);
            }
            return false;
        }
    }
}
