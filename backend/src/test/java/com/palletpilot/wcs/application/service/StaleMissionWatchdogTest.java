package com.palletpilot.wcs.application.service;

import com.palletpilot.wcs.domain.model.MovuMission;
import com.palletpilot.wcs.infrastructure.movu.MovuClient;
import com.palletpilot.wcs.infrastructure.movu.exception.MovuExceptions.MovuUnavailableException;
import com.palletpilot.wcs.infrastructure.persistence.repository.MovuMissionRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for StaleMissionWatchdog.
 * Verifies E-Stop safety, circuit breaker respect, active reconciliation,
 * and escalation to MANUAL INTERVENTION.
 */
@ExtendWith(MockitoExtension.class)
class StaleMissionWatchdogTest {

    @Mock
    private MovuMissionRepository missionRepository;

    @Mock
    private MovuClient movuClient;

    @Mock
    private SystemStateService systemStateService;

    @Mock
    private AlertService alertService;

    @InjectMocks
    private StaleMissionWatchdog watchdog;

    // ── Safety Guards ──

    @Test
    @DisplayName("skips when system is in E-STOP")
    void checkStaleMissions_eStop_skips() {
        when(systemStateService.isEmergencyStop()).thenReturn(true);

        watchdog.checkStaleMissions();

        verify(missionRepository, never()).findByStatusInAndUpdatedAtBefore(any(), any());
    }

    @Test
    @DisplayName("skips when circuit breaker is OPEN")
    void checkStaleMissions_circuitOpen_skips() {
        when(systemStateService.isEmergencyStop()).thenReturn(false);
        when(movuClient.getCircuitState()).thenReturn(CircuitBreaker.State.OPEN);

        watchdog.checkStaleMissions();

        verify(missionRepository, never()).findByStatusInAndUpdatedAtBefore(any(), any());
    }

    @Test
    @DisplayName("skips when no stale missions found")
    void checkStaleMissions_noStale_noAction() {
        when(systemStateService.isEmergencyStop()).thenReturn(false);
        when(movuClient.getCircuitState()).thenReturn(CircuitBreaker.State.CLOSED);
        when(missionRepository.findByStatusInAndUpdatedAtBefore(any(), any()))
                .thenReturn(Collections.emptyList());

        watchdog.checkStaleMissions();

        verify(movuClient, never()).getMissionStatus(any(), anyString());
    }

    // ── Active Reconciliation ──

    @Test
    @DisplayName("reconciles COMPLETED mission from aSTC")
    void checkStaleMissions_completedMission_reconciles() {
        UUID equipmentId = UUID.randomUUID();
        String missionId = "M-42";
        MovuMission stuck = buildStuckMission(missionId, equipmentId, 15);

        when(systemStateService.isEmergencyStop()).thenReturn(false);
        when(movuClient.getCircuitState()).thenReturn(CircuitBreaker.State.CLOSED);
        when(missionRepository.findByStatusInAndUpdatedAtBefore(any(), any()))
                .thenReturn(List.of(stuck));
        when(movuClient.getMissionStatus(equipmentId, missionId)).thenReturn("COMPLETED");

        watchdog.checkStaleMissions();

        verify(missionRepository).save(argThat(m -> "COMPLETED".equals(m.getStatus())));
    }

    @Test
    @DisplayName("reconciles FAILED mission and raises alert")
    void checkStaleMissions_failedMission_raisesAlert() {
        UUID equipmentId = UUID.randomUUID();
        String missionId = "M-99";
        MovuMission stuck = buildStuckMission(missionId, equipmentId, 15);

        when(systemStateService.isEmergencyStop()).thenReturn(false);
        when(movuClient.getCircuitState()).thenReturn(CircuitBreaker.State.CLOSED);
        when(missionRepository.findByStatusInAndUpdatedAtBefore(any(), any()))
                .thenReturn(List.of(stuck));
        when(movuClient.getMissionStatus(equipmentId, missionId)).thenReturn("FAILED");

        watchdog.checkStaleMissions();

        verify(missionRepository).save(argThat(m -> "FAILED".equals(m.getStatus())));
        verify(alertService).raiseAlert(eq(equipmentId), contains("FAILED"), eq("ERROR"));
    }

    // ── Escalation ──

    @Test
    @DisplayName("escalates UNKNOWN >30 min to MANUAL INTERVENTION")
    void checkStaleMissions_unknownOver30min_escalates() {
        UUID equipmentId = UUID.randomUUID();
        String missionId = "M-50";
        MovuMission stuck = buildStuckMission(missionId, equipmentId, 35); // 35 min stale

        when(systemStateService.isEmergencyStop()).thenReturn(false);
        when(movuClient.getCircuitState()).thenReturn(CircuitBreaker.State.CLOSED);
        when(missionRepository.findByStatusInAndUpdatedAtBefore(any(), any()))
                .thenReturn(List.of(stuck));
        when(movuClient.getMissionStatus(equipmentId, missionId)).thenReturn("UNKNOWN");

        watchdog.checkStaleMissions();

        verify(missionRepository).save(argThat(m -> "REQUIRES_MANUAL_INTERVENTION".equals(m.getStatus())));
        verify(alertService).raiseAlert(eq(equipmentId), contains("manual intervention"), eq("CRITICAL"));
    }

    // ── Circuit Breaker Resilience ──

    @Test
    @DisplayName("handles MovuUnavailableException gracefully per mission")
    void checkStaleMissions_circuitOpensInLoop_handlesGracefully() {
        UUID eq1 = UUID.randomUUID();
        String m1 = "M-100";
        String m2 = "M-101";
        MovuMission stuck1 = buildStuckMission(m1, eq1, 15);
        MovuMission stuck2 = buildStuckMission(m2, eq1, 15);

        when(systemStateService.isEmergencyStop()).thenReturn(false);
        when(movuClient.getCircuitState()).thenReturn(CircuitBreaker.State.CLOSED);
        when(missionRepository.findByStatusInAndUpdatedAtBefore(any(), any()))
                .thenReturn(List.of(stuck1, stuck2));
        // Both missions trigger unavailable — each caught inside handleStaleMission
        when(movuClient.getMissionStatus(eq(eq1), anyString()))
                .thenThrow(new MovuUnavailableException("aSTC down"));

        watchdog.checkStaleMissions();

        // Both missions attempted — exception is caught per-mission, loop continues
        verify(movuClient, times(2)).getMissionStatus(any(), anyString());
        // Neither mission is saved as FAILED (connectivity ≠ mission failure)
        verify(missionRepository, never()).save(any());
    }

    // ── Helpers ──

    private MovuMission buildStuckMission(String missionId, UUID equipmentId, int minutesStale) {
        MovuMission m = new MovuMission();
        m.setId(missionId);
        m.setEquipmentId(equipmentId);
        m.setStatus("RUNNING");
        m.setUpdatedAt(Instant.now().minus(minutesStale, ChronoUnit.MINUTES));
        return m;
    }
}
