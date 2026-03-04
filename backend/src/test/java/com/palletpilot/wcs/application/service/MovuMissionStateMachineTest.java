package com.palletpilot.wcs.application.service;

import com.palletpilot.wcs.domain.model.enums.MovuMissionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for MovuMissionStateMachine — pure validation, no mocks needed.
 * Covers all 13 state transition paths, regression rejection, idempotency,
 * legacy parsing, and UNKNOWN recovery.
 */
class MovuMissionStateMachineTest {

        private final MovuMissionStateMachine sm = new MovuMissionStateMachine();

        // ── Valid Forward Transitions ──

        @ParameterizedTest(name = "CREATED → {0}")
        @ValueSource(strings = { "ASSIGNING", "ASSIGNED", "CANCELLED", "ABORTED", "FAILED" })
        void fromCreated_validNextStates(String nextState) {
                assertThat(sm.canTransition("CREATED", MovuMissionStatus.valueOf(nextState))).isTrue();
        }

        @ParameterizedTest(name = "ASSIGNING → {0}")
        @ValueSource(strings = { "ASSIGNED", "CANCELLED", "ABORTED", "FAILED" })
        void fromAssigning_validNextStates(String nextState) {
                assertThat(sm.canTransition("ASSIGNING", MovuMissionStatus.valueOf(nextState))).isTrue();
        }

        @Test
        @DisplayName("happy path: CREATED → ASSIGNING → ASSIGNED → PICKED → COMPLETED")
        void happyPath_fullChain() {
                assertThat(sm.canTransition("CREATED", MovuMissionStatus.ASSIGNING)).isTrue();
                assertThat(sm.canTransition("ASSIGNING", MovuMissionStatus.ASSIGNED)).isTrue();
                assertThat(sm.canTransition("ASSIGNED", MovuMissionStatus.PICKED)).isTrue();
                assertThat(sm.canTransition("PICKED", MovuMissionStatus.COMPLETED)).isTrue();
        }

        @Test
        @DisplayName("late decision path: ASSIGNED → AWAITING_SOURCE → PICKED → AWAITING_DEST → COMPLETED")
        void lateDecisionPath() {
                assertThat(sm.canTransition("ASSIGNED", MovuMissionStatus.AWAITING_SOURCE_CONFIRM)).isTrue();
                assertThat(sm.canTransition("AWAITING_SOURCE_CONFIRM", MovuMissionStatus.PICKED)).isTrue();
                assertThat(sm.canTransition("PICKED", MovuMissionStatus.AWAITING_DEST_CONFIRM)).isTrue();
                assertThat(sm.canTransition("AWAITING_DEST_CONFIRM", MovuMissionStatus.COMPLETED)).isTrue();
        }

        // ── Terminal States ──

        @Test
        @DisplayName("COMPLETED is terminal → rejects everything")
        void completed_rejectsAll() {
                assertThat(sm.canTransition("COMPLETED", MovuMissionStatus.PICKED)).isFalse();
                assertThat(sm.canTransition("COMPLETED", MovuMissionStatus.CREATED)).isFalse();
                assertThat(sm.canTransition("COMPLETED", MovuMissionStatus.ASSIGNED)).isFalse();
        }

        @Test
        @DisplayName("CANCELLED is terminal → rejects everything")
        void cancelled_rejectsAll() {
                assertThat(sm.canTransition("CANCELLED", MovuMissionStatus.CREATED)).isFalse();
        }

        @Test
        @DisplayName("ABORTED → REQUIRES_MANUAL_INTERVENTION (escalation)")
        void aborted_canEscalate() {
                assertThat(sm.canTransition("ABORTED", MovuMissionStatus.REQUIRES_MANUAL_INTERVENTION)).isTrue();
        }

        @Test
        @DisplayName("FAILED → REQUIRES_MANUAL_INTERVENTION (escalation)")
        void failed_canEscalate() {
                assertThat(sm.canTransition("FAILED", MovuMissionStatus.REQUIRES_MANUAL_INTERVENTION)).isTrue();
        }

        // ── Regression Rejection ──

        @Test
        @DisplayName("rejects backward transition: PICKED → CREATED")
        void rejectsRegression_pickedToCreated() {
                assertThat(sm.canTransition("PICKED", MovuMissionStatus.CREATED)).isFalse();
        }

        @Test
        @DisplayName("rejects backward transition: ASSIGNED → ASSIGNING")
        void rejectsRegression_assignedToAssigning() {
                assertThat(sm.canTransition("ASSIGNED", MovuMissionStatus.ASSIGNING)).isFalse();
        }

        // ── Idempotency ──

        @Test
        @DisplayName("same state → false (no-op)")
        void sameState_idempotent() {
                assertThat(sm.canTransition("ASSIGNED", MovuMissionStatus.ASSIGNED)).isFalse();
                assertThat(sm.canTransition("COMPLETED", MovuMissionStatus.COMPLETED)).isFalse();
        }

        // ── Null Status ──

        @Test
        @DisplayName("null newStatus → false")
        void nullNewStatus_false() {
                assertThat(sm.canTransition("CREATED", null)).isFalse();
        }

        // ── UNKNOWN Recovery ──

        @Test
        @DisplayName("UNKNOWN current state → allows any forward transition")
        void unknownState_allowsAny() {
                assertThat(sm.canTransition("UNKNOWN", MovuMissionStatus.ASSIGNED)).isTrue();
                assertThat(sm.canTransition("UNKNOWN", MovuMissionStatus.COMPLETED)).isTrue();
        }

        // ── Legacy Status Parsing ──

        @Test
        @DisplayName("legacy 'RUNNING' maps to ASSIGNED")
        void legacyRunning_mapsToAssigned() {
                // RUNNING → ASSIGNED, so RUNNING → PICKED should be valid (ASSIGNED→PICKED)
                assertThat(sm.canTransition("RUNNING", MovuMissionStatus.PICKED)).isTrue();
        }

        @ParameterizedTest(name = "null/blank status → UNKNOWN → allows transitions")
        @NullAndEmptySource
        @ValueSource(strings = { " ", "  " })
        void nullOrBlankStatus_treatedAsUnknown(String status) {
                assertThat(sm.canTransition(status, MovuMissionStatus.CREATED)).isTrue();
        }

        @Test
        @DisplayName("unknown DB string → parsed as UNKNOWN → allows transitions")
        void unknownDbString_allowsTransitions() {
                assertThat(sm.canTransition("GARBAGE_STATE", MovuMissionStatus.COMPLETED)).isTrue();
        }

        // ── Forward Skip ──

        @Test
        @DisplayName("forward skip: CREATED → PICKED (skips ASSIGNING + ASSIGNED)")
        void forwardSkip_allowsWithWarning() {
                assertThat(sm.canTransition("CREATED", MovuMissionStatus.PICKED)).isTrue();
        }
}
