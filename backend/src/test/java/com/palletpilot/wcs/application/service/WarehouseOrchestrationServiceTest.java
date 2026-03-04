package com.palletpilot.wcs.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.palletpilot.wcs.domain.model.WarehouseLayout;
import com.palletpilot.wcs.domain.model.layout.LayoutGraph;
import com.palletpilot.wcs.domain.model.layout.LayoutNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WarehouseOrchestrationService — scheduled layout refresh.
 */
@ExtendWith(MockitoExtension.class)
class WarehouseOrchestrationServiceTest {

    @Mock
    private WarehouseLayoutService layoutService;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private WarehouseOrchestrationService service;

    @Test
    @DisplayName("refreshLayout: no layout → does nothing")
    void refreshLayout_noLayout_noOp() {
        when(layoutService.getLayout()).thenReturn(Optional.empty());

        service.refreshLayout();

        verifyNoInteractions(objectMapper);
    }

    @Test
    @DisplayName("refreshLayout: layout with null JSON → does nothing")
    void refreshLayout_nullJson_noOp() {
        WarehouseLayout layout = new WarehouseLayout();
        layout.setLayoutJson(null);
        when(layoutService.getLayout()).thenReturn(Optional.of(layout));

        service.refreshLayout();

        verifyNoInteractions(objectMapper);
    }

    @Test
    @DisplayName("refreshLayout: valid JSON → parses graph and checks triggers")
    void refreshLayout_validJson_parsesGraph() throws Exception {
        WarehouseLayout layout = new WarehouseLayout();
        layout.setLayoutJson("{\"nodes\":[],\"edges\":[]}");
        LayoutGraph graph = new LayoutGraph();
        graph.setNodes(List.of());
        graph.setEdges(List.of());

        when(layoutService.getLayout()).thenReturn(Optional.of(layout));
        when(objectMapper.readValue(eq("{\"nodes\":[],\"edges\":[]}"), eq(LayoutGraph.class)))
                .thenReturn(graph);

        service.refreshLayout();

        verify(objectMapper).readValue(any(String.class), eq(LayoutGraph.class));
    }

    @Test
    @DisplayName("refreshLayout: invalid JSON → handles gracefully (no exception)")
    void refreshLayout_invalidJson_handlesGracefully() throws Exception {
        WarehouseLayout layout = new WarehouseLayout();
        layout.setLayoutJson("INVALID");

        when(layoutService.getLayout()).thenReturn(Optional.of(layout));
        when(objectMapper.readValue(eq("INVALID"), eq(LayoutGraph.class)))
                .thenThrow(new com.fasterxml.jackson.core.JsonParseException(null, "bad json"));

        // Should not throw
        service.refreshLayout();
    }
}
