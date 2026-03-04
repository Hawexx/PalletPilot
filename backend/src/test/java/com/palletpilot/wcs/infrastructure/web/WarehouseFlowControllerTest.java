package com.palletpilot.wcs.infrastructure.web;

import com.palletpilot.wcs.domain.model.WarehouseLayout;
import com.palletpilot.wcs.infrastructure.persistence.repository.WarehouseLayoutRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WarehouseFlowController — singleton DEFAULT layout.
 */
@ExtendWith(MockitoExtension.class)
class WarehouseFlowControllerTest {

    @Mock
    private WarehouseLayoutRepository repository;
    @InjectMocks
    private WarehouseFlowController controller;

    @Test
    @DisplayName("getFlow: DEFAULT exists → returns it")
    void getFlow_defaultExists_returnsIt() {
        WarehouseLayout existing = WarehouseLayout.builder().id("DEFAULT").layoutJson("[{\"id\":1}]").build();
        when(repository.findById("DEFAULT")).thenReturn(Optional.of(existing));

        var resp = controller.getFlow();

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getLayoutJson()).contains("id");
    }

    @Test
    @DisplayName("getFlow: no DEFAULT → returns empty array layout")
    void getFlow_noDefault_returnsEmptyArray() {
        when(repository.findById("DEFAULT")).thenReturn(Optional.empty());

        var resp = controller.getFlow();

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getLayoutJson()).isEqualTo("[]");
    }

    @Test
    @DisplayName("saveFlow → saves with DEFAULT id and returns")
    void saveFlow_savesWithDefaultId() {
        when(repository.save(any(WarehouseLayout.class))).thenAnswer(inv -> inv.getArgument(0));

        var resp = controller.saveFlow("[{\"x\":5,\"y\":10}]");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(repository)
                .save(argThat(layout -> "DEFAULT".equals(layout.getId()) && layout.getLayoutJson().contains("x")));
    }
}
