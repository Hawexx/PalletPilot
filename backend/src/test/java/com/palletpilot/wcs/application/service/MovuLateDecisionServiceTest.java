package com.palletpilot.wcs.application.service;

import com.palletpilot.wcs.domain.model.MovuLaneReservation;
import com.palletpilot.wcs.domain.model.MovuLoadPosition;
import com.palletpilot.wcs.domain.model.MovuPendingConfirmation;
import com.palletpilot.wcs.domain.model.MovuPendingConfirmation.ConfirmationStatus;
import com.palletpilot.wcs.domain.model.MovuPendingConfirmation.ConfirmationType;
import com.palletpilot.wcs.infrastructure.movu.MovuClient;
import com.palletpilot.wcs.infrastructure.movu.config.MovuLaneGeometryProperties;
import com.palletpilot.wcs.infrastructure.movu.exception.MovuExceptions.MovuUnavailableException;
import com.palletpilot.wcs.infrastructure.persistence.repository.MovuLaneReservationRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.MovuLoadPositionRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.MovuPendingConfirmationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MovuLateDecisionService — safety-critical late decision flow.
 * Tests distance computation, slot reservation, aSTC confirmation,
 * circuit breaker retry, max attempts → CRITICAL alert.
 */
@ExtendWith(MockitoExtension.class)
class MovuLateDecisionServiceTest {

    @Mock
    private MovuClient movuClient;
    @Mock
    private MovuLaneGeometryProperties laneProps;
    @Mock
    private MovuPendingConfirmationRepository pendingRepo;
    @Mock
    private MovuLaneReservationRepository reservationRepo;
    @Mock
    private MovuLoadPositionRepository loadPositionRepo;
    @Mock
    private AlertService alertService;

    @InjectMocks
    private MovuLateDecisionService service;

    private final UUID equipmentId = UUID.randomUUID();

    @BeforeEach
    void setUpLaneGeometry() {
        // Standard lane: 12m, 1200mm pallet, 150mm gap, 200mm offsets
        lenient().when(laneProps.getLaneLengthMm()).thenReturn(12000);
        lenient().when(laneProps.getMaxLoadLengthMm()).thenReturn(1200);
        lenient().when(laneProps.getDefaultGapMm()).thenReturn(150);
        lenient().when(laneProps.getOffsetFrontMm()).thenReturn(200);
        lenient().when(laneProps.getOffsetBackMm()).thenReturn(200);
    }

    // ── Happy path: DESTINATION confirmation ──

    @Test
    @DisplayName("process destination: happy path → compute, reserve, update, confirm, SUCCEEDED")
    void process_destination_happyPath() {
        MovuPendingConfirmation pending = buildPending(ConfirmationType.DESTINATION);
        when(movuClient.getMissionStatus(equipmentId, "MSN-1"))
                .thenReturn("MissionAwaitingDestinationEntryConfirmation");
        when(loadPositionRepo.findByLevelAndLaneAndSection(1, 2, 3)).thenReturn(List.of());
        when(reservationRepo.findByEquipmentIdAndLevelAndLaneAndSectionAndExpiresAtAfter(
                eq(equipmentId), eq(1), eq(2), eq(3), any())).thenReturn(List.of());

        service.process(pending);

        verify(movuClient).updateDestination(eq(equipmentId), eq("MSN-1"), any());
        verify(movuClient).confirmEnterDestination(equipmentId, "MSN-1");
        assertThat(pending.getStatus()).isEqualTo(ConfirmationStatus.SUCCEEDED);
    }

    // ── Happy path: SOURCE confirmation ──

    @Test
    @DisplayName("process source: confirms via confirmEnterSource")
    void process_source_happyPath() {
        MovuPendingConfirmation pending = buildPending(ConfirmationType.SOURCE);
        when(movuClient.getMissionStatus(equipmentId, "MSN-1"))
                .thenReturn("MissionAwaitingSourceEntryConfirmation");
        when(loadPositionRepo.findByLevelAndLaneAndSection(1, 2, 3)).thenReturn(List.of());
        when(reservationRepo.findByEquipmentIdAndLevelAndLaneAndSectionAndExpiresAtAfter(
                eq(equipmentId), eq(1), eq(2), eq(3), any())).thenReturn(List.of());

        service.process(pending);

        verify(movuClient).updateSource(eq(equipmentId), eq("MSN-1"), any());
        verify(movuClient).confirmEnterSource(equipmentId, "MSN-1");
        assertThat(pending.getStatus()).isEqualTo(ConfirmationStatus.SUCCEEDED);
    }

    // ── Mission no longer awaiting → skip ──

    @Test
    @DisplayName("process: mission no longer awaiting → marks SUCCEEDED without confirmation")
    void process_notAwaiting_skips() {
        MovuPendingConfirmation pending = buildPending(ConfirmationType.DESTINATION);
        when(movuClient.getMissionStatus(equipmentId, "MSN-1")).thenReturn("MissionCompleted");

        service.process(pending);

        verify(movuClient, never()).updateDestination(any(), any(), any());
        verify(movuClient, never()).confirmEnterDestination(any(), any());
        assertThat(pending.getStatus()).isEqualTo(ConfirmationStatus.SUCCEEDED);
    }

    // ── Reuse reserved distance on retry ──

    @Test
    @DisplayName("process retry: reuses previously reserved distance (no recompute)")
    void process_retry_reusesReservedDistance() {
        MovuPendingConfirmation pending = buildPending(ConfirmationType.DESTINATION);
        pending.setReservedDistanceMm(5000); // Previously reserved
        pending.setAttemptCount(1);

        when(movuClient.getMissionStatus(equipmentId, "MSN-1"))
                .thenReturn("MissionAwaitingDestinationEntryConfirmation");

        service.process(pending);

        verify(loadPositionRepo, never()).findByLevelAndLaneAndSection(anyInt(), anyInt(), anyInt());
        verify(movuClient).updateDestination(eq(equipmentId), eq("MSN-1"),
                argThat(map -> (int) map.get("distance") == 5000));
        assertThat(pending.getStatus()).isEqualTo(ConfirmationStatus.SUCCEEDED);
    }

    // ── Circuit breaker → defer retry ──

    @Test
    @DisplayName("process: circuit breaker open → PENDING with 30s retry delay")
    void process_circuitBreakerOpen_defersRetry() {
        MovuPendingConfirmation pending = buildPending(ConfirmationType.DESTINATION);
        when(movuClient.getMissionStatus(equipmentId, "MSN-1"))
                .thenThrow(new MovuUnavailableException("circuit breaker open"));

        service.process(pending);

        assertThat(pending.getStatus()).isEqualTo(ConfirmationStatus.PENDING);
        assertThat(pending.getNextRetryAt()).isNotNull();
        assertThat(pending.getErrorMessage()).contains("Circuit breaker");
    }

    // ── Max attempts → CRITICAL alert ──

    @Test
    @DisplayName("process: max attempts exceeded → FAILED + CRITICAL alert")
    void process_maxAttempts_failsWithAlert() {
        MovuPendingConfirmation pending = buildPending(ConfirmationType.DESTINATION);
        pending.setAttemptCount(2); // Will become 3 (MAX_ATTEMPTS)

        when(movuClient.getMissionStatus(equipmentId, "MSN-1"))
                .thenReturn("MissionAwaitingDestinationEntryConfirmation");
        when(loadPositionRepo.findByLevelAndLaneAndSection(1, 2, 3)).thenReturn(List.of());
        when(reservationRepo.findByEquipmentIdAndLevelAndLaneAndSectionAndExpiresAtAfter(
                eq(equipmentId), eq(1), eq(2), eq(3), any())).thenReturn(List.of());
        // Simulate aSTC failure after reservation
        doThrow(new RuntimeException("aSTC timeout"))
                .when(movuClient).updateDestination(any(), any(), any());

        service.process(pending);

        assertThat(pending.getStatus()).isEqualTo(ConfirmationStatus.FAILED);
        verify(alertService).raiseAlert(eq(equipmentId), contains("MSN-1"), eq("CRITICAL"));
    }

    // ── Cleanup ──

    @Test
    @DisplayName("cleanupReservations: deletes by missionId")
    void cleanupReservations_deletesByMission() {
        service.cleanupReservations("MSN-X");
        verify(reservationRepo).deleteByMissionId("MSN-X");
    }

    @Test
    @DisplayName("cleanupExpiredReservations: deletes expired")
    void cleanupExpired_deletesExpired() {
        service.cleanupExpiredReservations();
        verify(reservationRepo).deleteByExpiresAtBefore(any());
    }

    // ── Helpers ──

    private MovuPendingConfirmation buildPending(ConfirmationType type) {
        return MovuPendingConfirmation.builder()
                .id(UUID.randomUUID())
                .equipmentId(equipmentId)
                .missionId("MSN-1")
                .confirmationType(type)
                .status(ConfirmationStatus.PENDING)
                .level(1)
                .lane(2)
                .section(3)
                .attemptCount(0)
                .build();
    }
}
