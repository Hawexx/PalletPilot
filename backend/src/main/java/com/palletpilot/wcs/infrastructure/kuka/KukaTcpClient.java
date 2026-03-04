package com.palletpilot.wcs.infrastructure.kuka;

import com.palletpilot.wcs.domain.model.Equipment;
import com.palletpilot.wcs.domain.model.EquipmentCapabilities;
import com.palletpilot.wcs.domain.port.out.HardwareClient;
import com.palletpilot.wcs.infrastructure.persistence.repository.EquipmentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Production-grade TCP Client for KUKA Robots.
 * Implements the line-based CMD/ACK/DONE/ERR protocol
 * defined in docs/KUKA_TCP_ICD.md.
 *
 * Protocol:
 * WCS → KUKA: CMD:<id>:<type>:<params>\n
 * KUKA → WCS: ACK:<id>\n
 * KUKA → WCS: DONE:<id>\n or ERR:<id>:<code>:<text>\n
 */
@Component("kukaClient")
@RequiredArgsConstructor
@Slf4j
public class KukaTcpClient implements HardwareClient {

    private final EquipmentRepository equipmentRepository;
    private final ObjectMapper objectMapper;

    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 3000;
    private static final int DEFAULT_ACK_TIMEOUT_MS = 5000;
    private static final int DEFAULT_DONE_TIMEOUT_MS = 30_000;

    @Override
    public CompletableFuture<Boolean> sendCommand(UUID equipmentId, String command) {
        final String correlationId = UUID.randomUUID().toString().substring(0, 8);
        final String commandId = UUID.randomUUID().toString().substring(0, 8);

        return CompletableFuture.supplyAsync(() -> {
            Equipment eq = equipmentRepository.findById(equipmentId).orElse(null);
            if (eq == null) {
                log.error("[KUKA] cid={} Equipment not found: {}", correlationId, equipmentId);
                return false;
            }

            EquipmentCapabilities caps = parseCapabilities(eq);
            int doneTimeout = caps.getEffectiveTimeoutMs(DEFAULT_DONE_TIMEOUT_MS);

            try (Socket socket = new Socket()) {
                socket.connect(
                        new InetSocketAddress(eq.getIpAddress(), eq.getPort()),
                        DEFAULT_CONNECT_TIMEOUT_MS);

                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // === Step 1: Send command ===
                String cmdLine = String.format("CMD:%s:%s", commandId, command);
                out.println(cmdLine);
                log.info("[KUKA] cid={} equipment={} SENT: {}", correlationId, eq.getName(), cmdLine);

                // === Step 2: Wait for ACK ===
                socket.setSoTimeout(DEFAULT_ACK_TIMEOUT_MS);
                String ackLine = in.readLine();

                if (ackLine == null) {
                    log.error("[KUKA] cid={} equipment={} Connection closed before ACK", correlationId, eq.getName());
                    return false;
                }

                if (!ackLine.startsWith("ACK:" + commandId)) {
                    log.error("[KUKA] cid={} equipment={} Unexpected ACK: {}", correlationId, eq.getName(), ackLine);
                    return false;
                }

                log.info("[KUKA] cid={} equipment={} ACK received", correlationId, eq.getName());

                // === Step 3: Wait for DONE or ERR ===
                socket.setSoTimeout(doneTimeout);
                String resultLine = in.readLine();

                if (resultLine == null) {
                    log.error("[KUKA] cid={} equipment={} Connection closed before DONE/ERR", correlationId,
                            eq.getName());
                    return false;
                }

                if (resultLine.startsWith("DONE:" + commandId)) {
                    log.info("[KUKA] cid={} equipment={} DONE", correlationId, eq.getName());
                    return true;
                }

                if (resultLine.startsWith("ERR:" + commandId)) {
                    // Parse ERR:<id>:<code>:<text>
                    String[] parts = resultLine.split(":", 4);
                    String errorCode = parts.length > 2 ? parts[2] : "unknown";
                    String errorText = parts.length > 3 ? parts[3] : "unknown error";
                    log.error("[KUKA] cid={} equipment={} ERROR code={} text={}",
                            correlationId, eq.getName(), errorCode, errorText);
                    return false;
                }

                log.warn("[KUKA] cid={} equipment={} Unexpected response: {}", correlationId, eq.getName(), resultLine);
                return false;

            } catch (java.net.SocketTimeoutException e) {
                log.error("[KUKA] cid={} equipment={} TIMEOUT: {}", correlationId, eq.getName(), e.getMessage());
                return false;
            } catch (Exception e) {
                log.error("[KUKA] cid={} equipment={} ERROR: {}", correlationId, eq.getName(), e.getMessage());
                return false;
            }
        });
    }

    @Override
    public boolean checkHealth(UUID equipmentId) {
        Equipment eq = equipmentRepository.findById(equipmentId).orElse(null);
        if (eq == null || eq.getIpAddress() == null || eq.getPort() == null)
            return false;

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(eq.getIpAddress(), eq.getPort()), 1500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private EquipmentCapabilities parseCapabilities(Equipment eq) {
        if (eq.getConfigJson() == null || eq.getConfigJson().isBlank()) {
            return new EquipmentCapabilities();
        }
        try {
            return objectMapper.readValue(eq.getConfigJson(), EquipmentCapabilities.class);
        } catch (Exception e) {
            log.warn("[KUKA] Failed to parse capabilities for {}: {}", eq.getName(), e.getMessage());
            return new EquipmentCapabilities();
        }
    }
}
