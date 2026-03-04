package com.palletpilot.wcs.application.service;

import com.palletpilot.wcs.domain.model.MovuPendingConfirmation;
import com.palletpilot.wcs.domain.model.MovuPendingConfirmation.ConfirmationStatus;
import com.palletpilot.wcs.infrastructure.persistence.repository.MovuPendingConfirmationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Scheduled job that processes pending confirmations.
 * Runs every 5 seconds, picks up PENDING/FAILED rows ready for retry.
 * Uses DB locking (FOR UPDATE SKIP LOCKED) for concurrency safety.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MovuPendingConfirmationScheduler {

    private final MovuPendingConfirmationRepository pendingRepo;
    private final MovuLateDecisionService lateDecisionService;

    @Scheduled(fixedDelay = 5000) // Every 5 seconds
    @Transactional
    public void processPendingConfirmations() {
        // Set a correlation ID for this scheduled run
        MDC.put("correlationId", "SCHED-" + UUID.randomUUID().toString().substring(0, 8));

        try {
            List<MovuPendingConfirmation> ready = pendingRepo.findReadyForProcessing(
                    List.of(ConfirmationStatus.PENDING, ConfirmationStatus.FAILED),
                    Instant.now());

            if (ready.isEmpty())
                return;

            log.info("Processing {} pending confirmation(s)", ready.size());

            for (MovuPendingConfirmation pending : ready) {
                try {
                    lateDecisionService.process(pending);
                } catch (Exception e) {
                    log.error("Scheduler error processing confirmation for mission {}: {}",
                            pending.getMissionId(), e.getMessage());
                }
            }
        } finally {
            MDC.remove("correlationId");
        }
    }

    /**
     * Clean up expired lane reservations every 60 seconds.
     */
    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void cleanupExpiredReservations() {
        lateDecisionService.cleanupExpiredReservations();
    }
}
