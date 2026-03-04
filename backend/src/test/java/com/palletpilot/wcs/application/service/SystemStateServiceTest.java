package com.palletpilot.wcs.application.service;

import com.palletpilot.wcs.domain.model.enums.SystemState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link SystemStateService}.
 * Validates strict state-machine rules, especially E-STOP safety.
 */
@ExtendWith(MockitoExtension.class)
class SystemStateServiceTest {

    @Mock
    private ConfigurationService configService;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private SystemStateService service;

    // ── Basic State Tests ─────────────────────────────────

    @Test
    @DisplayName("initial state should be STOPPED")
    void testInitialState() {
        assertThat(service.getCurrentState()).isEqualTo(SystemState.STOPPED);
        assertThat(service.isRunning()).isFalse();
    }

    @Test
    @DisplayName("STOPPED → RUNNING transition should succeed")
    void testStoppedToRunning() {
        service.transitionTo(SystemState.RUNNING, "admin", "start system");
        assertThat(service.getCurrentState()).isEqualTo(SystemState.RUNNING);
        assertThat(service.isRunning()).isTrue();
    }

    @Test
    @DisplayName("same state transition should be no-op")
    void testSameStateNoOp() {
        // Already STOPPED, transition to STOPPED = no-op
        service.transitionTo(SystemState.STOPPED, "admin", "test");
        assertThat(service.getCurrentState()).isEqualTo(SystemState.STOPPED);
    }

    // ── E-STOP Safety Tests ───────────────────────────────

    @Test
    @DisplayName("E-STOP can be triggered from any state")
    void testEmergencyStopFromAnyState() {
        service.transitionTo(SystemState.RUNNING, "admin", "start");
        service.transitionTo(SystemState.EMERGENCY_STOP, "operator", "emergency");

        assertThat(service.isEmergencyStop()).isTrue();
        assertThat(service.getLastTriggeredBy()).isEqualTo("operator");
    }

    @Test
    @DisplayName("SAFETY: E-STOP → RUNNING direct transition should be BLOCKED")
    void testEmergencyStopToRunningBlocked() {
        service.transitionTo(SystemState.RUNNING, "admin", "start");
        service.transitionTo(SystemState.EMERGENCY_STOP, "operator", "emergency");

        assertThatThrownBy(() -> service.transitionTo(SystemState.RUNNING, "admin", "try resume"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SAFETY VIOLATION");
    }

    @Test
    @DisplayName("E-STOP → STOPPED → RUNNING recovery path should work")
    void testEmergencyStopRecoveryPath() {
        service.transitionTo(SystemState.RUNNING, "admin", "start");
        service.transitionTo(SystemState.EMERGENCY_STOP, "operator", "emergency");
        service.transitionTo(SystemState.STOPPED, "admin", "reset");
        service.transitionTo(SystemState.RUNNING, "admin", "resume");

        assertThat(service.isRunning()).isTrue();
    }

    // ── DEGRADED Mode Tests ───────────────────────────────

    @Test
    @DisplayName("RUNNING → DEGRADED transition should succeed")
    void testRunningToDegraded() {
        service.transitionTo(SystemState.RUNNING, "admin", "start");
        service.transitionTo(SystemState.DEGRADED, "system", "equipment failure");

        assertThat(service.getCurrentState()).isEqualTo(SystemState.DEGRADED);
    }

    @Test
    @DisplayName("DEGRADED → RUNNING recovery should succeed")
    void testDegradedToRunningRecovery() {
        service.transitionTo(SystemState.RUNNING, "admin", "start");
        service.transitionTo(SystemState.DEGRADED, "system", "equipment failure");
        service.transitionTo(SystemState.RUNNING, "admin", "recovered");

        assertThat(service.isRunning()).isTrue();
    }

    // ── Metadata Tests ────────────────────────────────────

    @Test
    @DisplayName("lastTriggeredBy and lastTransitionTime should update on transition")
    void testMetadataUpdate() {
        service.transitionTo(SystemState.RUNNING, "admin_user", "test");

        assertThat(service.getLastTriggeredBy()).isEqualTo("admin_user");
        assertThat(service.getLastTransitionTime()).isNotNull();
    }
}
