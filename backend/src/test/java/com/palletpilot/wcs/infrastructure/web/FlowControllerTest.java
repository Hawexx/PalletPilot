package com.palletpilot.wcs.infrastructure.web;

import com.palletpilot.wcs.application.service.FlowExecutionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FlowController.
 * Tests conveyor control, flow execution, and validation logic.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class FlowControllerTest {

    @Mock
    private FlowExecutionService flowExecutionService;

    @InjectMocks
    private FlowController controller;

    // ── Conveyor Control ──

    @Test
    @DisplayName("startConveyor → 200 on success with commandId")
    void startConveyor_success_returns200() {
        when(flowExecutionService.startConveyor("CONV-01", "FORWARD"))
                .thenReturn("cmd-001");

        ResponseEntity<Map<String, Object>> response = controller.startConveyor(
                "CONV-01", Map.of("direction", "FORWARD"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("success")).isEqualTo(true);
        assertThat(response.getBody().get("commandId")).isEqualTo("cmd-001");
    }

    @Test
    @DisplayName("startConveyor → 400 when service returns null (failure)")
    void startConveyor_failure_returns400() {
        when(flowExecutionService.startConveyor("CONV-01", null)).thenReturn(null);

        ResponseEntity<Map<String, Object>> response = controller.startConveyor(
                "CONV-01", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("success")).isEqualTo(false);
    }

    @Test
    @DisplayName("stopConveyor → 200 on success")
    void stopConveyor_success_returns200() {
        when(flowExecutionService.stopConveyor("CONV-02")).thenReturn("cmd-002");

        ResponseEntity<Map<String, Object>> response = controller.stopConveyor("CONV-02");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("success")).isEqualTo(true);
    }

    // ── Flow Execution ──

    @Test
    @DisplayName("executeInboundFlow → 400 when palletId missing")
    void executeInboundFlow_missingPalletId_returns400() {
        ResponseEntity<Map<String, Object>> response = controller.executeInboundFlow(
                Map.of("foo", "bar"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("success")).isEqualTo(false);
    }

    @Test
    @DisplayName("executeInboundFlow → 200 with command IDs on success")
    void executeInboundFlow_success_returns200() {
        when(flowExecutionService.executeFlowFromStart("ZONE", "PLT-001"))
                .thenReturn(List.of("cmd-a", "cmd-b"));

        ResponseEntity<Map<String, Object>> response = controller.executeInboundFlow(
                Map.of("palletId", "PLT-001"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("stepsExecuted")).isEqualTo(2);
    }

    @Test
    @DisplayName("executeFlowFromNode → generates palletId when missing")
    void executeFlowFromNode_missingPalletId_generatesOne() {
        when(flowExecutionService.executeFlowFromStart(eq("WRAPPER"), anyString()))
                .thenReturn(List.of("cmd-x"));

        ResponseEntity<Map<String, Object>> response = controller.executeFlowFromNode(
                "wrapper", Map.of());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((String) response.getBody().get("palletId")).startsWith("PALLET-");
    }
}
