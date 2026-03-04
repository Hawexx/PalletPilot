package com.palletpilot.wcs.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.palletpilot.wcs.domain.model.Equipment;
import com.palletpilot.wcs.infrastructure.persistence.repository.EquipmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * FlowExecutionService interprets the warehouse flow layout (from FlowEditor)
 * and routes commands to equipment based on the configured flow paths.
 * 
 * The warehouse layout JSON contains:
 * - nodes: Equipment items with id, type, config (including IP, port, triggers)
 * - edges: Connections between nodes (source -> target)
 * 
 * This service:
 * 1. Loads the current flow layout
 * 2. Finds the next equipment in the flow sequence
 * 3. Sends appropriate PLC commands based on node type and config
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FlowExecutionService {

    private final ConfigurationService configService;
    private final EquipmentService equipmentService;
    private final EquipmentRepository equipmentRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Execute the flow starting from a specific node (e.g., INBOUND zone)
     * 
     * @param startNodeType The type of node to start from (e.g., "ZONE" with
     *                      zoneType "INBOUND")
     * @param palletId      The pallet ID being moved through the flow
     * @return List of command IDs executed
     */
    public List<String> executeFlowFromStart(String startNodeType, String palletId) {
        List<String> commandIds = new ArrayList<>();

        try {
            JsonNode layout = loadFlowLayout();
            if (layout == null) {
                log.warn("No flow layout configured");
                return commandIds;
            }

            JsonNode nodes = layout.get("nodes");
            JsonNode edges = layout.get("edges");

            if (nodes == null || edges == null) {
                log.warn("Invalid layout structure");
                return commandIds;
            }

            // Find starting node
            JsonNode startNode = findNodeByType(nodes, startNodeType);
            if (startNode == null) {
                log.warn("Start node of type {} not found in layout", startNodeType);
                return commandIds;
            }

            // Execute flow from start node
            String currentNodeId = startNode.get("id").asText();
            executeNodeAndContinue(currentNodeId, nodes, edges, palletId, commandIds, new HashSet<>());

        } catch (Exception e) {
            log.error("Flow execution error: {}", e.getMessage(), e);
        }

        return commandIds;
    }

    /**
     * Execute a specific node's command and continue to connected nodes
     */
    private void executeNodeAndContinue(String nodeId, JsonNode nodes, JsonNode edges,
            String palletId, List<String> commandIds, Set<String> visited) {
        if (visited.contains(nodeId)) {
            log.debug("Already visited node {}, skipping to prevent loops", nodeId);
            return;
        }
        visited.add(nodeId);

        // Find the node
        JsonNode node = findNodeById(nodes, nodeId);
        if (node == null)
            return;

        // Execute command based on node type
        String commandId = executeNodeCommand(node, palletId);
        if (commandId != null) {
            commandIds.add(commandId);
        }

        // Find and execute next nodes in the flow
        List<String> nextNodeIds = findConnectedNodes(nodeId, edges);
        for (String nextNodeId : nextNodeIds) {
            executeNodeAndContinue(nextNodeId, nodes, edges, palletId, commandIds, visited);
        }
    }

    /**
     * Execute a command on a node based on its type and configuration
     */
    private String executeNodeCommand(JsonNode node, String palletId) {
        String nodeType = node.get("type").asText();
        String nodeLabel = node.has("label") ? node.get("label").asText() : nodeType;
        JsonNode config = node.has("config") ? node.get("config") : null;

        log.info("Executing node: {} ({})", nodeLabel, nodeType);

        try {
            // Try to find matching equipment by IP address
            if (config != null && config.has("ip") && !config.get("ip").asText().isEmpty()) {
                String ip = config.get("ip").asText();
                // Port might be used in future for more specific addressing
                // int port = config.has("port") ? config.get("port").asInt() : 502;

                Equipment equipment = findEquipmentByIp(ip);
                if (equipment != null) {
                    // Build command payload based on node type
                    String commandPayload = buildCommandPayload(nodeType, config, palletId);
                    return equipmentService.executeCommand(equipment.getId(), commandPayload);
                } else {
                    log.warn("No equipment found with IP: {}. Simulating command.", ip);
                    return simulateCommand(nodeType, nodeLabel, palletId);
                }
            } else {
                // No IP configured - simulate or skip
                log.debug("Node {} has no IP configured, simulating", nodeLabel);
                return simulateCommand(nodeType, nodeLabel, palletId);
            }
        } catch (Exception e) {
            log.error("Failed to execute command on node {}: {}", nodeLabel, e.getMessage());
            return null;
        }
    }

    /**
     * Build the command payload based on node type
     */
    private String buildCommandPayload(String nodeType, JsonNode config, String palletId) {
        Map<String, Object> command = new LinkedHashMap<>();
        command.put("palletId", palletId);
        command.put("timestamp", System.currentTimeMillis());

        switch (nodeType) {
            case "CONVEYOR":
                command.put("action", "START");
                command.put("direction",
                        config.has("flowDirection") ? config.get("flowDirection").asText() : "FORWARD");
                command.put("speed", config.has("speed") ? config.get("speed").asDouble() : 0.5);
                break;

            case "TURNTABLE":
                command.put("action", "ROTATE");
                command.put("degrees", 90);
                command.put("speed", config.has("rotationSpeed") ? config.get("rotationSpeed").asDouble() : 45);
                break;

            case "KUKA":
            case "MOVU":
                command.put("action", "PICKUP");
                if (config.has("trigger")) {
                    command.put("trigger", config.get("trigger").asText());
                }
                break;

            case "WRAPPER":
                command.put("action", "WRAP");
                command.put("cycles", 3);
                break;

            case "SCANNER":
                command.put("action", "SCAN");
                break;

            case "ZONE":
                String zoneType = config.has("zoneType") ? config.get("zoneType").asText() : "STAGING";
                command.put("action", "ZONE_" + zoneType);
                if (config.has("throughput")) {
                    command.put("throughput", config.get("throughput").asInt());
                }
                break;

            case "TRANSFER_CAR":
                command.put("action", "TRANSFER");
                command.put("speed", config.has("speed") ? config.get("speed").asDouble() : 1.0);
                break;

            case "PALLET_BUFFER":
                command.put("action", "BUFFER");
                if (config.has("palletType")) {
                    command.put("palletType", config.get("palletType").asText());
                }
                break;

            default:
                command.put("action", "EXECUTE");
        }

        try {
            return objectMapper.writeValueAsString(command);
        } catch (Exception e) {
            return "{\"action\":\"EXECUTE\",\"palletId\":\"" + palletId + "\"}";
        }
    }

    /**
     * Simulate command execution when no real equipment is connected
     */
    private String simulateCommand(String nodeType, String nodeLabel, String palletId) {
        String simCommandId = "SIM-" + UUID.randomUUID().toString().substring(0, 8);
        log.info("[SIMULATED] {} ({}) processing pallet {}: Command ID {}",
                nodeLabel, nodeType, palletId, simCommandId);
        return simCommandId;
    }

    /**
     * Start conveyor by node ID from the flow layout
     */
    public String startConveyor(String nodeId, String direction) {
        try {
            JsonNode layout = loadFlowLayout();
            if (layout == null)
                return null;

            JsonNode node = findNodeById(layout.get("nodes"), nodeId);
            if (node == null || !"CONVEYOR".equals(node.get("type").asText())) {
                log.warn("Node {} is not a conveyor", nodeId);
                return null;
            }

            JsonNode config = node.has("config") ? node.get("config") : null;
            if (config != null && config.has("ip") && !config.get("ip").asText().isEmpty()) {
                Equipment equipment = findEquipmentByIp(config.get("ip").asText());
                if (equipment != null) {
                    String payload = String.format(
                            "{\"action\":\"START\",\"direction\":\"%s\",\"speed\":%.1f}",
                            direction != null ? direction : "FORWARD",
                            config.has("speed") ? config.get("speed").asDouble() : 0.5);
                    return equipmentService.executeCommand(equipment.getId(), payload);
                }
            }

            return simulateCommand("CONVEYOR", node.get("label").asText(), "MANUAL_START");
        } catch (Exception e) {
            log.error("Error starting conveyor {}: {}", nodeId, e.getMessage());
            return null;
        }
    }

    /**
     * Stop conveyor by node ID
     */
    public String stopConveyor(String nodeId) {
        try {
            JsonNode layout = loadFlowLayout();
            if (layout == null)
                return null;

            JsonNode node = findNodeById(layout.get("nodes"), nodeId);
            if (node == null)
                return null;

            JsonNode config = node.has("config") ? node.get("config") : null;
            if (config != null && config.has("ip") && !config.get("ip").asText().isEmpty()) {
                Equipment equipment = findEquipmentByIp(config.get("ip").asText());
                if (equipment != null) {
                    return equipmentService.executeCommand(equipment.getId(), "{\"action\":\"STOP\"}");
                }
            }

            return simulateCommand("CONVEYOR", node.get("label").asText(), "MANUAL_STOP");
        } catch (Exception e) {
            log.error("Error stopping conveyor {}: {}", nodeId, e.getMessage());
            return null;
        }
    }

    /**
     * Get all conveyors from the flow layout
     */
    public List<Map<String, Object>> getConveyors() {
        List<Map<String, Object>> conveyors = new ArrayList<>();
        try {
            JsonNode layout = loadFlowLayout();
            if (layout == null || !layout.has("nodes"))
                return conveyors;

            for (JsonNode node : layout.get("nodes")) {
                if ("CONVEYOR".equals(node.get("type").asText())) {
                    Map<String, Object> conveyor = new LinkedHashMap<>();
                    conveyor.put("id", node.get("id").asText());
                    conveyor.put("label", node.has("label") ? node.get("label").asText() : "Conveyor");
                    conveyor.put("flowDirection",
                            node.has("flowDirection") ? node.get("flowDirection").asText() : "RIGHT");
                    if (node.has("config")) {
                        JsonNode config = node.get("config");
                        conveyor.put("speed", config.has("speed") ? config.get("speed").asDouble() : 0.5);
                        conveyor.put("ip", config.has("ip") ? config.get("ip").asText() : null);
                    }
                    conveyors.add(conveyor);
                }
            }
        } catch (Exception e) {
            log.error("Error getting conveyors: {}", e.getMessage());
        }
        return conveyors;
    }

    // ---- Helper Methods ----

    private JsonNode loadFlowLayout() {
        try {
            String layoutJson = configService.getString("warehouse.flow.layout", "");
            if (layoutJson != null && !layoutJson.isEmpty()) {
                return objectMapper.readTree(layoutJson);
            }
        } catch (Exception e) {
            log.error("Failed to load flow layout: {}", e.getMessage());
        }
        return null;
    }

    private JsonNode findNodeById(JsonNode nodes, String nodeId) {
        if (nodes == null)
            return null;
        for (JsonNode node : nodes) {
            if (nodeId.equals(node.get("id").asText())) {
                return node;
            }
        }
        return null;
    }

    private JsonNode findNodeByType(JsonNode nodes, String nodeType) {
        if (nodes == null)
            return null;
        for (JsonNode node : nodes) {
            if (nodeType.equals(node.get("type").asText())) {
                return node;
            }
        }
        return null;
    }

    private List<String> findConnectedNodes(String sourceId, JsonNode edges) {
        List<String> targets = new ArrayList<>();
        if (edges == null)
            return targets;

        for (JsonNode edge : edges) {
            if (sourceId.equals(edge.get("source").asText())) {
                targets.add(edge.get("target").asText());
            }
        }
        return targets;
    }

    private Equipment findEquipmentByIp(String ip) {
        List<Equipment> all = equipmentRepository.findAll();
        return all.stream()
                .filter(e -> ip.equals(e.getIpAddress()))
                .findFirst()
                .orElse(null);
    }
}
