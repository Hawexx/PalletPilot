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

@Component("siemensClient")
@RequiredArgsConstructor
@Slf4j
public class SiemensClient implements HardwareClient {

    private final EquipmentRepository equipmentRepository;
    private final ObjectMapper objectMapper;

    @Override
    public CompletableFuture<Boolean> sendCommand(UUID equipmentId, String command) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Equipment eq = equipmentRepository.findById(equipmentId)
                        .orElseThrow(() -> new IllegalArgumentException("Equipment not found"));

                // S7 Connection String: s7://{ip}/{rack}/{slot}
                // Defaults: Rack 0, Slot 1
                String connectionString = String.format("s7://%s/0/1", eq.getIpAddress());

                log.info("Sending S7 Command to {}: {}", eq.getName(), command);

                // Protocol: similar to Modbus, write to a Data Block (DB)
                // e.g. "%DB1.DBD0:INT"
                String targetTag = "%DB1.DBD0:INT"; // Default

                if (eq.getConfigJson() != null) {
                    JsonNode root = objectMapper.readTree(eq.getConfigJson());
                    if (root.has("s7Tag")) {
                        targetTag = root.get("s7Tag").asText();
                    }
                }

                int valueToWrite = 1;
                try {
                    valueToWrite = Integer.parseInt(command);
                } catch (NumberFormatException e) {
                    // Ignore
                }

                try (PlcConnection plcConnection = new PlcDriverManager().getConnection(connectionString)) {
                    if (!plcConnection.isConnected()) {
                        throw new IllegalStateException("Failed to connect to S7: " + connectionString);
                    }

                    PlcWriteResponse response = plcConnection.writeRequestBuilder()
                            .addItem("value", targetTag, valueToWrite)
                            .build()
                            .execute()
                            .get(2000, TimeUnit.MILLISECONDS);

                    return response.getResponseCode("value") == PlcResponseCode.OK;
                }

            } catch (Exception e) {
                log.error("Siemens S7 Error for {}: {}", equipmentId, e.getMessage());
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
            String connectionString = String.format("s7://%s/0/1", eq.getIpAddress());
            try (PlcConnection plcConnection = new PlcDriverManager().getConnection(connectionString)) {
                return plcConnection.isConnected();
            }
        } catch (Exception e) {
            return false;
        }
    }
}
