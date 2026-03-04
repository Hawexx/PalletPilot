package com.palletpilot.wcs.application.service;

import com.palletpilot.wcs.domain.model.enums.SystemState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Slf4j
@RequiredArgsConstructor
public class SystemStateService {

    private final ConfigurationService configService;
    private final AuditService auditService;

    // Default to STOPPED for safety on startup
    private final AtomicReference<SystemState> currentState = new AtomicReference<>(SystemState.STOPPED);
    private final AtomicReference<String> lastTriggeredBy = new AtomicReference<>("SYSTEM");
    private final AtomicReference<Instant> lastTransitionTime = new AtomicReference<>(Instant.now());

    public SystemState getCurrentState() {
        return currentState.get();
    }

    public void transitionTo(SystemState newState, String userId, String reason) {
        SystemState oldState = currentState.get();

        if (oldState == newState) {
            log.warn("System is already in state {}", newState);
            return;
        }

        // Strict Transition Rules
        verifyTransition(oldState, newState);

        currentState.set(newState);
        lastTriggeredBy.set(userId);
        lastTransitionTime.set(Instant.now());

        log.info("SYSTEM STATE CHANGE: {} -> {} | By: {} | Reason: {}", oldState, newState, userId, reason);

        // Persist audit trail
        auditService.log("SYSTEM_STATE_CHANGE", "SystemState", newState.name(), userId,
                String.format("{\"oldState\":\"%s\",\"newState\":\"%s\",\"reason\":\"%s\"}", oldState, newState,
                        reason));
        log.info("[AUDIT] state_change={{old={}, new={}, user={}, reason={}, ts={}}}",
                oldState, newState, userId, reason, lastTransitionTime.get());
    }

    private void verifyTransition(SystemState oldState, SystemState newState) {
        // E-STOP can be triggered from ANY state
        if (newState == SystemState.EMERGENCY_STOP) {
            return;
        }

        // DEGRADED can be entered from RUNNING (equipment failure)
        // or returned to RUNNING from DEGRADED (recovery)
        if (newState == SystemState.DEGRADED && oldState == SystemState.RUNNING) {
            return;
        }
        if (newState == SystemState.RUNNING && oldState == SystemState.DEGRADED) {
            return;
        }

        // Must go through STOPPED to reset E-STOP
        if (oldState == SystemState.EMERGENCY_STOP && newState == SystemState.RUNNING) {
            throw new IllegalStateException(
                    "SAFETY VIOLATION: Cannot transition directly from EMERGENCY_STOP to RUNNING. Must reset to STOPPED first.");
        }

        // Other transitions are generally allowed (STOPPED <-> RUNNING)
    }

    public boolean isRunning() {
        return currentState.get() == SystemState.RUNNING;
    }

    public boolean isEmergencyStop() {
        return currentState.get() == SystemState.EMERGENCY_STOP;
    }

    // Info for UI
    public String getLastTriggeredBy() {
        return lastTriggeredBy.get();
    }

    public Instant getLastTransitionTime() {
        return lastTransitionTime.get();
    }
}
