package com.palletpilot.wcs.infrastructure.plc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.palletpilot.wcs.domain.model.Equipment;
import com.palletpilot.wcs.domain.port.out.HardwareClient;
import com.palletpilot.wcs.infrastructure.persistence.repository.EquipmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.plc4x.java.PlcDriverManager;
import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.api.messages.PlcWriteResponse;
import org.apache.plc4x.java.api.types.PlcResponseCode;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component("modbusClient")
@RequiredArgsConstructor
@Slf4j
public class ModbusClient implements HardwareClient {

    private final EquipmentRepository equipmentRepository;
    private final ObjectMapper objectMapper;

    @Override
    public CompletableFuture<Boolean> sendCommand(UUID equipmentId, String command) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Equipment eq = equipmentRepository.findById(equipmentId)
                        .orElseThrow(() -> new IllegalArgumentException("Equipment not found"));

                // Modbus Connection String: modbus-tcp://{ip}:{port}
                String connectionString = String.format("modbus-tcp://%s:%d",
                        eq.getIpAddress(),
                        eq.getPort() != null ? eq.getPort() : 502);

                log.info("Sending Modbus Command to {}: {}", eq.getName(), command);

                // Protocol: Write Command to Configured Register (e.g., "holding-register:1")
                // Assumption: commnad is Integer for Modbus? Or we parse it.
                // For flexibility, let's assume command="register:value" e.g. "1:99"
                // Or retrieve target register from ConfigJson.

                String targetRegister = "holding-register:1"; // Default
                int valueToWrite = 1;

                if (eq.getConfigJson() != null) {
                    JsonNode root = objectMapper.readTree(eq.getConfigJson());
                    if (root.has("modbusRegister")) {
                        targetRegister = root.get("modbusRegister").asText();
                    }
                }

                try {
                    valueToWrite = Integer.parseInt(command);
                } catch (NumberFormatException e) {
                    log.warn("Modbus command {} is not an integer, writing 1 as trigger", command);
                }

                try (PlcConnection plcConnection = new PlcDriverManager().getConnection(connectionString)) {
                    if (!plcConnection.isConnected()) {
                        throw new IllegalStateException("Failed to connect to Modbus: " + connectionString);
                    }

                    PlcWriteResponse response = plcConnection.writeRequestBuilder()
                            .addItem("value", targetRegister, valueToWrite)
                            .build()
                            .execute()
                            .get(2000, TimeUnit.MILLISECONDS);

                    return response.getResponseCode("value") == PlcResponseCode.OK;
                }

            } catch (Exception e) {
                log.error("Modbus Error for {}: {}", equipmentId, e.getMessage());
                return false;
            }
        });
    }

    @Override
    public boolean checkHealth(UUID equipmentId) {
        try {
            Equipment eq = equipmentRepository.findById(equipmentId).orElse(null);
            if (eq == null)
                return false;
            String connectionString = String.format("modbus-tcp://%s:%d", eq.getIpAddress(),
                    eq.getPort() != null ? eq.getPort() : 502);
            try (PlcConnection plcConnection = new PlcDriverManager().getConnection(connectionString)) {
                return plcConnection.isConnected();
            }
        } catch (Exception e) {
            return false;
        }
    }
}
