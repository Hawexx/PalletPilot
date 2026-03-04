package com.palletpilot.wcs.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.palletpilot.wcs.domain.model.WarehouseLayout;
import com.palletpilot.wcs.domain.model.layout.LayoutGraph;
import com.palletpilot.wcs.domain.model.layout.LayoutNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class WarehouseOrchestrationService {

    private final WarehouseLayoutService layoutService;
    private final ObjectMapper objectMapper;

    private LayoutGraph currentGraph;

    /**
     * Periodically refresh the layout to ensure the orchestrator is running on the
     * latest configuration.
     * In a real system, this might be event-driven (e.g., triggered by a save
     * event).
     */
    @Scheduled(fixedDelay = 10000)
    public void refreshLayout() {
        Optional<WarehouseLayout> layoutOpt = layoutService.getLayout();
        if (layoutOpt.isEmpty() || layoutOpt.get().getLayoutJson() == null) {
            log.debug("No active warehouse layout found.");
            return;
        }

        try {
            String json = layoutOpt.get().getLayoutJson();
            // Don't re-parse if hash matches (optimization left for later)
            this.currentGraph = objectMapper.readValue(json, LayoutGraph.class);
            log.debug("Orchestrator loaded layout with {} nodes and {} edges",
                    currentGraph.getNodes().size(), currentGraph.getEdges().size());

            checkTriggers();

        } catch (Exception e) {
            log.error("Failed to parse warehouse layout JSON", e);
        }
    }

    private void checkTriggers() {
        if (currentGraph == null)
            return;

        // Example Logic: Iterate nodes and check specific configurations
        for (LayoutNode node : currentGraph.getNodes()) {
            if ("MOVU".equals(node.getType())) {
                Object trigger = node.getConfig() != null ? node.getConfig().get("trigger") : null;
                if (trigger != null) {
                    log.debug("Checking trigger criteria for Movu Node {}: {}", node.getLabel(), trigger);
                    // STUB: Trigger evaluation requires PLC event stream integration.
                    // When PLC events (e.g. PALLET_READY) are available, evaluate
                    // trigger.toString() against real-time sensor data here.
                    log.warn(
                            "[ORCHESTRATION] Trigger '{}' on node '{}' not evaluated — PLC event stream not yet connected",
                            trigger, node.getLabel());
                }
            }
        }
    }
}
