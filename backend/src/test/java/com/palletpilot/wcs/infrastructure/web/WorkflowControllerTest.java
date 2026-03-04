package com.palletpilot.wcs.infrastructure.web;

import com.palletpilot.wcs.application.service.WorkflowService;
import com.palletpilot.wcs.domain.model.enums.ProcessState;
import com.palletpilot.wcs.domain.model.workflow.WorkflowDefinition;
import com.palletpilot.wcs.domain.model.workflow.WorkflowInstance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WorkflowController.
 */
@ExtendWith(MockitoExtension.class)
class WorkflowControllerTest {

    @Mock
    private WorkflowService workflowService;

    @InjectMocks
    private WorkflowController controller;

    @Test
    @DisplayName("createDefinition → delegates to service and returns 200")
    void createDefinition_returns200() {
        WorkflowDefinition def = WorkflowDefinition.builder()
                .id(UUID.randomUUID()).name("Inbound Flow").build();
        when(workflowService.createDefinition(any())).thenReturn(def);

        ResponseEntity<WorkflowDefinition> response = controller.createDefinition(def);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getName()).isEqualTo("Inbound Flow");
    }

    @Test
    @DisplayName("startWorkflow → creates instance with definitionId and optional orderId")
    void startWorkflow_returns200() {
        UUID defId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        WorkflowInstance instance = WorkflowInstance.builder()
                .id(UUID.randomUUID()).status(ProcessState.RUNNING).build();
        when(workflowService.instantiateWorkflow(defId, orderId)).thenReturn(instance);

        ResponseEntity<WorkflowInstance> response = controller.startWorkflow(defId, orderId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(ProcessState.RUNNING);
        verify(workflowService).instantiateWorkflow(defId, orderId);
    }

    @Test
    @DisplayName("startWorkflow → works with null orderId")
    void startWorkflow_nullOrderId() {
        UUID defId = UUID.randomUUID();
        WorkflowInstance instance = WorkflowInstance.builder()
                .id(UUID.randomUUID()).status(ProcessState.RUNNING).build();
        when(workflowService.instantiateWorkflow(defId, null)).thenReturn(instance);

        ResponseEntity<WorkflowInstance> response = controller.startWorkflow(defId, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
