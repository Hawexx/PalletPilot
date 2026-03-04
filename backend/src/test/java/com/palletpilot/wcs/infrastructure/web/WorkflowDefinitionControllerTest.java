package com.palletpilot.wcs.infrastructure.web;

import com.palletpilot.wcs.application.service.WorkflowService;
import com.palletpilot.wcs.domain.model.workflow.WorkflowDefinition;
import com.palletpilot.wcs.infrastructure.persistence.repository.WorkflowDefinitionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkflowDefinitionControllerTest {

    @Mock
    private WorkflowService workflowService;
    @Mock
    private WorkflowDefinitionRepository repository;
    @InjectMocks
    private WorkflowDefinitionController controller;

    @Test
    @DisplayName("getAll → returns list")
    void getAll_returnsList() {
        WorkflowDefinition def = WorkflowDefinition.builder().name("test-wf").build();
        when(repository.findAll()).thenReturn(List.of(def));

        var result = controller.getAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("test-wf");
    }

    @Test
    @DisplayName("getById: found → 200")
    void getById_found_200() {
        UUID id = UUID.randomUUID();
        WorkflowDefinition def = WorkflowDefinition.builder().id(id).name("wf-1").build();
        when(repository.findById(id)).thenReturn(Optional.of(def));

        var resp = controller.getById(id);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("getById: not found → 404")
    void getById_notFound_404() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        var resp = controller.getById(id);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("createOrUpdate → 200")
    void createOrUpdate_200() {
        WorkflowDefinition def = WorkflowDefinition.builder().name("new-wf").build();
        when(workflowService.createDefinition(def)).thenReturn(def);

        var resp = controller.createOrUpdate(def);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("delete → 204")
    void delete_204() {
        UUID id = UUID.randomUUID();

        var resp = controller.delete(id);

        verify(repository).deleteById(id);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
