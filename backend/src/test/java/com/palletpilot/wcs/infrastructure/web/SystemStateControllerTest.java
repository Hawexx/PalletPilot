package com.palletpilot.wcs.infrastructure.web;

import com.palletpilot.wcs.application.service.SystemStateService;
import com.palletpilot.wcs.domain.model.enums.SystemState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SystemStateController.
 * Validates state retrieval, transition logic, and E-Stop safety.
 */
@ExtendWith(MockitoExtension.class)
class SystemStateControllerTest {

    @Mock
    private SystemStateService systemStateService;

    @InjectMocks
    private SystemStateController controller;

    // ── GET /system/state ──

    @Test
    @DisplayName("getState → returns current state with all fields")
    void getState_returnsFullState() {
        when(systemStateService.getCurrentState()).thenReturn(SystemState.RUNNING);
        when(systemStateService.isRunning()).thenReturn(true);
        when(systemStateService.isEmergencyStop()).thenReturn(false);
        when(systemStateService.getLastTransitionTime()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        when(systemStateService.getLastTriggeredBy()).thenReturn("admin");

        ResponseEntity<Map<String, Object>> response = controller.getState();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("state")).isEqualTo(SystemState.RUNNING);
        assertThat(body.get("isRunning")).isEqualTo(true);
        assertThat(body.get("isEStop")).isEqualTo(false);
        assertThat(body.get("lastTriggeredBy")).isEqualTo("admin");
    }

    // ── POST /system/state ──

    @Test
    @DisplayName("changeState → transitions successfully and returns 200")
    void changeState_success() {
        ResponseEntity<String> response = controller.changeState(
                SystemState.RUNNING, "operator1", "Shift start");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("RUNNING");
        verify(systemStateService).transitionTo(SystemState.RUNNING, "operator1", "Shift start");
    }

    @Test
    @DisplayName("changeState → E-Stop transition is allowed")
    void changeState_eStop() {
        ResponseEntity<String> response = controller.changeState(
                SystemState.EMERGENCY_STOP, "safety_operator", "Emergency detected");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(systemStateService).transitionTo(SystemState.EMERGENCY_STOP, "safety_operator", "Emergency detected");
    }

    @Test
    @DisplayName("changeState → invalid transition returns 400 Bad Request")
    void changeState_invalidTransition_returns400() {
        doThrow(new IllegalStateException("Cannot transition from EMERGENCY_STOP to STOPPED"))
                .when(systemStateService).transitionTo(eq(SystemState.STOPPED), anyString(), anyString());

        ResponseEntity<String> response = controller.changeState(
                SystemState.STOPPED, "user", "Trying to reset");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Cannot transition");
    }
}
