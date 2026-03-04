package com.palletpilot.wcs.infrastructure.web;

import com.palletpilot.wcs.application.service.WarehouseLayoutService;
import com.palletpilot.wcs.domain.model.WarehouseLayout;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WarehouseLayoutControllerTest {

    @Mock
    private WarehouseLayoutService service;
    @InjectMocks
    private WarehouseLayoutController controller;

    @Test
    @DisplayName("getLayout: exists → 200")
    void getLayout_exists_200() {
        WarehouseLayout layout = WarehouseLayout.builder().id("layout-1").layoutJson("{\"nodes\":[]}").build();
        when(service.getLayout()).thenReturn(Optional.of(layout));

        var resp = controller.getLayout();

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
    }

    @Test
    @DisplayName("getLayout: empty → 204")
    void getLayout_empty_204() {
        when(service.getLayout()).thenReturn(Optional.empty());

        var resp = controller.getLayout();

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    @DisplayName("saveLayout → 200")
    void saveLayout_returns200() {
        WarehouseLayout saved = WarehouseLayout.builder().id("layout-2").layoutJson("{\"test\":true}").build();
        when(service.saveLayout("{\"test\":true}")).thenReturn(saved);

        var resp = controller.saveLayout(new WarehouseLayoutController.LayoutRequest("{\"test\":true}"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
    }
}
