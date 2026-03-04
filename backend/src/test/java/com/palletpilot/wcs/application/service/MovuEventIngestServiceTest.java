package com.palletpilot.wcs.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.palletpilot.wcs.domain.model.MovuLoadPosition;
import com.palletpilot.wcs.domain.model.MovuMission;
import com.palletpilot.wcs.domain.model.MovuMissionEvent;
import com.palletpilot.wcs.domain.model.MovuPendingConfirmation;
import com.palletpilot.wcs.domain.model.enums.MovuMissionStatus;
import com.palletpilot.wcs.infrastructure.movu.dto.MovuLocation;
import com.palletpilot.wcs.infrastructure.movu.dto.MovuMissionPayload;
import com.palletpilot.wcs.infrastructure.movu.dto.MovuNotificationEnvelope;
import com.palletpilot.wcs.infrastructure.persistence.repository.MovuLoadPositionRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.MovuMissionEventRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.MovuMissionRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.MovuPendingConfirmationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MovuEventIngestService — the core webhook event processing
 * engine.
 * Tests idempotency, state machine validation, event dispatch, and error
 * handling.
 */
@ExtendWith(MockitoExtension.class)
class MovuEventIngestServiceTest {

    @Mock
    private MovuMissionEventRepository eventRepository;
    @Mock
    private MovuMissionRepository missionRepository;
    @Mock
    private MovuLoadPositionRepository loadPositionRepository;
    @Mock
    private MovuPendingConfirmationRepository pendingConfirmationRepository;
    @Mock
    private MovuMissionStateMachine stateMachine;
    @Mock
    private MovuRemediationService remediationService;
    @Mock
    private AlertService alertService;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private WebSocketBroadcastService wsBroadcast;
    @Mock
    private MovuService movuService;

    @InjectMocks
    private MovuEventIngestService ingestService;

    // ── Idempotency ──

    @Test
    @DisplayName("duplicate notificationId → returns false, no processing")
    void ingest_duplicate_returnsFalse() {
        MovuNotificationEnvelope env = buildEnvelope("notif-dup", "MissionCompleted", "MSN-1");
        when(eventRepository.existsByNotificationId("notif-dup")).thenReturn(true);

        boolean result = ingestService.ingest(env);

        assertThat(result).isFalse();
        verify(missionRepository, never()).findById(any());
    }

    @Test
    @DisplayName("new notificationId → returns true, persists event")
    void ingest_new_returnsTrue() {
        MovuNotificationEnvelope env = buildEnvelope("notif-new", "MissionAssigned", "MSN-2");
        when(eventRepository.existsByNotificationId("notif-new")).thenReturn(false);
        when(eventRepository.save(any())).thenReturn(new MovuMissionEvent());
        when(missionRepository.findById("MSN-2")).thenReturn(Optional.empty());
        when(missionRepository.save(any())).thenReturn(new MovuMission());

        boolean result = ingestService.ingest(env);

        assertThat(result).isTrue();
        verify(eventRepository).save(any(MovuMissionEvent.class));
    }

    // ── Missing Mission ID ──

    @Test
    @DisplayName("envelope with no missionId → stores event only")
    void ingest_noMissionId_storesEventOnly() {
        MovuNotificationEnvelope env = MovuNotificationEnvelope.builder()
                .notificationId("notif-no-mission")
                .notificationType("SystemEvent")
                .build();
        when(eventRepository.existsByNotificationId("notif-no-mission")).thenReturn(false);
        when(eventRepository.save(any())).thenReturn(new MovuMissionEvent());

        boolean result = ingestService.ingest(env);

        assertThat(result).isTrue();
        verify(missionRepository, never()).findById(any());
    }

    // ── Known Mission → State Transition ──

    @Test
    @DisplayName("known mission + valid transition → updates status and broadcasts")
    void ingest_knownMission_validTransition() {
        MovuNotificationEnvelope env = buildEnvelope("notif-3", "MissionAssigned", "MSN-3");
        MovuMission mission = buildMission("MSN-3", "CREATED");

        when(eventRepository.existsByNotificationId("notif-3")).thenReturn(false);
        when(eventRepository.save(any())).thenReturn(new MovuMissionEvent());
        when(missionRepository.findById("MSN-3")).thenReturn(Optional.of(mission));
        when(stateMachine.canTransition("CREATED", MovuMissionStatus.ASSIGNED)).thenReturn(true);

        ingestService.ingest(env);

        verify(missionRepository).save(mission);
        assertThat(mission.getStatus()).isEqualTo("ASSIGNED");
        verify(wsBroadcast).sendMovuEvent(eq("MISSION_UPDATE"), any());
    }

    // ── Unknown Mission → Creates Tracking Record ──

    @Test
    @DisplayName("unknown mission → creates tracking record with EXTERNAL locations")
    void ingest_unknownMission_createsTracking() {
        MovuNotificationEnvelope env = buildEnvelope("notif-4", "MissionPicked", "MSN-EXT");
        when(eventRepository.existsByNotificationId("notif-4")).thenReturn(false);
        when(eventRepository.save(any())).thenReturn(new MovuMissionEvent());
        when(missionRepository.findById("MSN-EXT")).thenReturn(Optional.empty());
        when(missionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ingestService.ingest(env);

        verify(missionRepository).save(argThat(m -> ((MovuMission) m).getId().equals("MSN-EXT")
                && ((MovuMission) m).getSku().equals("UNKNOWN")));
    }

    // ── MissionError → Remediation Delegation ──

    @Test
    @DisplayName("MissionError → delegates to remediationService")
    void ingest_missionError_delegatesToRemediation() {
        MovuNotificationEnvelope env = buildEnvelope("notif-5", "MissionError", "MSN-ERR");
        MovuMission mission = buildMission("MSN-ERR", "IN_PROGRESS");
        mission.setEquipmentId(UUID.randomUUID());

        when(eventRepository.existsByNotificationId("notif-5")).thenReturn(false);
        when(eventRepository.save(any())).thenReturn(new MovuMissionEvent());
        when(missionRepository.findById("MSN-ERR")).thenReturn(Optional.of(mission));
        when(stateMachine.canTransition(anyString(), any())).thenReturn(true);

        ingestService.ingest(env);

        verify(remediationService).handleMissionError(
                eq("MSN-ERR"), anyString(), anyString(), any(UUID.class));
    }

    // ── MissionError + Remediation Failure → Fallback Alert ──

    @Test
    @DisplayName("MissionError + remediation failure → fallback alert raised")
    void ingest_missionError_remediationFails_fallbackAlert() {
        MovuNotificationEnvelope env = buildEnvelope("notif-6", "MissionError", "MSN-ERR2");
        MovuMission mission = buildMission("MSN-ERR2", "IN_PROGRESS");
        UUID eqId = UUID.randomUUID();
        mission.setEquipmentId(eqId);

        when(eventRepository.existsByNotificationId("notif-6")).thenReturn(false);
        when(eventRepository.save(any())).thenReturn(new MovuMissionEvent());
        when(missionRepository.findById("MSN-ERR2")).thenReturn(Optional.of(mission));
        when(stateMachine.canTransition(anyString(), any())).thenReturn(true);
        doThrow(new RuntimeException("remediation boom"))
                .when(remediationService).handleMissionError(any(), any(), any(), any());

        ingestService.ingest(env);

        verify(alertService).raiseAlert(eq(eqId), contains("MSN-ERR2"), eq("ERROR"));
    }

    // ── MissionAborted → Alert ──

    @Test
    @DisplayName("MissionAborted → raises WARNING alert")
    void ingest_missionAborted_raisesAlert() {
        MovuNotificationEnvelope env = buildEnvelope("notif-7", "MissionAborted", "MSN-ABORT");
        MovuMission mission = buildMission("MSN-ABORT", "IN_PROGRESS");
        UUID eqId = UUID.randomUUID();
        mission.setEquipmentId(eqId);

        when(eventRepository.existsByNotificationId("notif-7")).thenReturn(false);
        when(eventRepository.save(any())).thenReturn(new MovuMissionEvent());
        when(missionRepository.findById("MSN-ABORT")).thenReturn(Optional.of(mission));
        when(stateMachine.canTransition(anyString(), any())).thenReturn(true);

        ingestService.ingest(env);

        verify(alertService).raiseAlert(eq(eqId), contains("aborted"), eq("WARNING"));
    }

    // ── MissionDropped → Handoff Trigger ──

    @Test
    @DisplayName("MissionDropped with targetLocation → triggers handoff")
    void ingest_missionDropped_triggersHandoff() {
        MovuNotificationEnvelope env = buildEnvelope("notif-8", "MissionDropped", "MSN-DROP");
        MovuMission mission = buildMission("MSN-DROP", "PICKED");
        mission.setTargetLocation("CONV-1");
        mission.setEquipmentId(UUID.randomUUID());

        when(eventRepository.existsByNotificationId("notif-8")).thenReturn(false);
        when(eventRepository.save(any())).thenReturn(new MovuMissionEvent());
        when(missionRepository.findById("MSN-DROP")).thenReturn(Optional.of(mission));
        when(stateMachine.canTransition(anyString(), any())).thenReturn(true);

        ingestService.ingest(env);

        verify(movuService).triggerHandoff("CONV-1");
    }

    // ── Helpers ──

    private MovuNotificationEnvelope buildEnvelope(String notifId, String type, String missionId) {
        MovuMissionPayload payload = MovuMissionPayload.builder()
                .missionId(missionId)
                .build();
        return MovuNotificationEnvelope.builder()
                .notificationId(notifId)
                .notificationType(type)
                .timeStamp("2026-01-15T10:30:00Z")
                .missionPayload(payload)
                .build();
    }

    private MovuMission buildMission(String id, String status) {
        return MovuMission.builder()
                .id(id)
                .sku("TEST-SKU")
                .status(status)
                .build();
    }
}
