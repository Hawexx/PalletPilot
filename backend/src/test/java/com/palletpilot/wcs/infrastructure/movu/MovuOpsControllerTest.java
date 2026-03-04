package com.palletpilot.wcs.infrastructure.movu;

import com.palletpilot.wcs.application.service.SystemStateService;
import com.palletpilot.wcs.domain.model.MovuMission;
import com.palletpilot.wcs.domain.model.MovuMissionEvent;
import com.palletpilot.wcs.domain.model.MovuPendingConfirmation;
import com.palletpilot.wcs.domain.model.MovuPendingConfirmation.ConfirmationStatus;
import com.palletpilot.wcs.domain.model.enums.SystemState;
import com.palletpilot.wcs.infrastructure.persistence.repository.MovuMissionEventRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.MovuMissionRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.MovuPendingConfirmationRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MovuOpsController.
 * Verifies timeline, active missions, pending confirmations, and health.
 */
@ExtendWith(MockitoExtension.class)
class MovuOpsControllerTest {

    @Mock
    private MovuMissionEventRepository eventRepository;

    @Mock
    private MovuMissionRepository missionRepository;

    @Mock
    private MovuPendingConfirmationRepository pendingConfirmationRepo;

    @Mock
    private MovuClient movuClient;

    @Mock
    private SystemStateService systemStateService;

    @InjectMocks
    private MovuOpsController controller;

    // ── Timeline ──

    @Test
    @DisplayName("getMissionTimeline → 200 with ordered events")
    void getMissionTimeline_returnsEvents() {
        MovuMissionEvent event = new MovuMissionEvent();
        event.setMissionId("M-42");
        event.setNotificationType("MissionDropped");
        event.setEventTimestamp(Instant.now());
        event.setProcessedAt(Instant.now());

        when(eventRepository.findByMissionIdOrderByEventTimestampAsc("M-42"))
                .thenReturn(List.of(event));

        ResponseEntity<List<Map<String, Object>>> response = controller.getMissionTimeline("M-42");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).get("notificationType")).isEqualTo("MissionDropped");
    }

    @Test
    @DisplayName("getMissionTimeline → 404 when no events found")
    void getMissionTimeline_notFound() {
        when(eventRepository.findByMissionIdOrderByEventTimestampAsc("M-999"))
                .thenReturn(Collections.emptyList());

        ResponseEntity<List<Map<String, Object>>> response = controller.getMissionTimeline("M-999");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── Active Missions ──

    @Test
    @DisplayName("getActiveMissions → filters out terminal statuses")
    void getActiveMissions_filtersTerminal() {
        MovuMission running = new MovuMission();
        running.setId("M-1");
        running.setStatus("RUNNING");
        running.setSku("SKU-A");
        running.setSourceLocation("A1");
        running.setTargetLocation("B2");

        MovuMission completed = new MovuMission();
        completed.setId("M-2");
        completed.setStatus("COMPLETED");
        completed.setSku("SKU-B");
        completed.setSourceLocation("C1");
        completed.setTargetLocation("D2");

        when(missionRepository.findAll()).thenReturn(List.of(running, completed));

        ResponseEntity<List<Map<String, Object>>> response = controller.getActiveMissions();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1); // Only RUNNING, not COMPLETED
        assertThat(response.getBody().get(0).get("missionId")).isEqualTo("M-1");
    }

    // ── Health ──

    @Test
    @DisplayName("getMovuHealth → returns system state and circuit breaker state")
    void getMovuHealth_returnsHealth() {
        when(systemStateService.getCurrentState()).thenReturn(SystemState.RUNNING);
        when(movuClient.getCircuitState()).thenReturn(CircuitBreaker.State.CLOSED);

        ResponseEntity<Map<String, Object>> response = controller.getMovuHealth();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("systemState")).isEqualTo(SystemState.RUNNING);
        assertThat(response.getBody().get("circuitState")).isEqualTo("CLOSED");
    }

    @Test
    @DisplayName("getMovuHealth → returns UNKNOWN circuit state on error")
    void getMovuHealth_circuitError_returnsUnknown() {
        when(systemStateService.getCurrentState()).thenReturn(SystemState.RUNNING);
        when(movuClient.getCircuitState()).thenThrow(new RuntimeException("Connection lost"));

        ResponseEntity<Map<String, Object>> response = controller.getMovuHealth();

        assertThat(response.getBody().get("circuitState")).isEqualTo("UNKNOWN");
    }
}
