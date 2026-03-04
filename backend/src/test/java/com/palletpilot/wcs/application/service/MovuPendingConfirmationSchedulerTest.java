package com.palletpilot.wcs.application.service;

import com.palletpilot.wcs.domain.model.MovuPendingConfirmation;
import com.palletpilot.wcs.domain.model.MovuPendingConfirmation.ConfirmationStatus;
import com.palletpilot.wcs.infrastructure.persistence.repository.MovuPendingConfirmationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MovuPendingConfirmationScheduler.
 * Tests scheduled processing of pending late-decision confirmations,
 * error isolation, MDC lifecycle, and reservation cleanup.
 */
@ExtendWith(MockitoExtension.class)
class MovuPendingConfirmationSchedulerTest {

    @Mock
    private MovuPendingConfirmationRepository pendingRepo;
    @Mock
    private MovuLateDecisionService lateDecisionService;

    @InjectMocks
    private MovuPendingConfirmationScheduler scheduler;

    @Test
    @DisplayName("no pending confirmations → no processing, MDC clean")
    void processPending_empty_noProcessing() {
        when(pendingRepo.findReadyForProcessing(any(), any())).thenReturn(List.of());

        scheduler.processPendingConfirmations();

        verify(lateDecisionService, never()).process(any());
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    @DisplayName("pending confirmations → delegates each to lateDecisionService")
    void processPending_delegates() {
        MovuPendingConfirmation p1 = buildPending("MSN-1");
        MovuPendingConfirmation p2 = buildPending("MSN-2");
        when(pendingRepo.findReadyForProcessing(any(), any())).thenReturn(List.of(p1, p2));

        scheduler.processPendingConfirmations();

        verify(lateDecisionService, times(2)).process(any());
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    @DisplayName("one confirmation throws → continues processing rest, MDC still cleaned")
    void processPending_errorIsolation() {
        MovuPendingConfirmation p1 = buildPending("MSN-FAIL");
        MovuPendingConfirmation p2 = buildPending("MSN-OK");
        when(pendingRepo.findReadyForProcessing(any(), any())).thenReturn(List.of(p1, p2));
        doThrow(new RuntimeException("boom")).when(lateDecisionService).process(p1);

        scheduler.processPendingConfirmations();

        // p2 was still processed despite p1 failure
        verify(lateDecisionService).process(p1);
        verify(lateDecisionService).process(p2);
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    @DisplayName("cleanupExpiredReservations → delegates to lateDecisionService")
    void cleanupReservations_delegates() {
        scheduler.cleanupExpiredReservations();

        verify(lateDecisionService).cleanupExpiredReservations();
    }

    private MovuPendingConfirmation buildPending(String missionId) {
        return MovuPendingConfirmation.builder()
                .id(UUID.randomUUID())
                .equipmentId(UUID.randomUUID())
                .missionId(missionId)
                .confirmationType(MovuPendingConfirmation.ConfirmationType.DESTINATION)
                .status(ConfirmationStatus.PENDING)
                .build();
    }
}
