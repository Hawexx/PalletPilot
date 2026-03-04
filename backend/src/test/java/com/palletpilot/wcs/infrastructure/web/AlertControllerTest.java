package com.palletpilot.wcs.infrastructure.web;

import com.palletpilot.wcs.application.service.AlertService;
import com.palletpilot.wcs.domain.model.Alert;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AlertController.
 */
@ExtendWith(MockitoExtension.class)
class AlertControllerTest {

    @Mock
    private AlertService alertService;

    @InjectMocks
    private AlertController controller;

    @Test
    @DisplayName("getAllAlerts → returns 200 with alerts")
    void getAllAlerts_returns200() {
        Alert alert = Alert.builder().id(UUID.randomUUID()).message("Conveyor jam").build();
        when(alertService.getAllAlerts()).thenReturn(List.of(alert));

        ResponseEntity<Iterable<Alert>> response = controller.getAllAlerts();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    @DisplayName("getAllAlerts → returns 200 with empty list")
    void getAllAlerts_empty() {
        when(alertService.getAllAlerts()).thenReturn(Collections.emptyList());

        ResponseEntity<Iterable<Alert>> response = controller.getAllAlerts();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    @DisplayName("deleteAlert → returns 204 and delegates to service")
    void deleteAlert_returns204() {
        UUID id = UUID.randomUUID();

        ResponseEntity<Void> response = controller.deleteAlert(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(alertService).deleteAlert(id);
    }

    @Test
    @DisplayName("deleteAllAlerts → returns 204 and clears all")
    void deleteAllAlerts_returns204() {
        ResponseEntity<Void> response = controller.deleteAllAlerts();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(alertService).deleteAllAlerts();
    }
}
