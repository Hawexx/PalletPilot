package com.palletpilot.wcs.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only event log for Movu mission webhook notifications.
 * The notification_id UNIQUE constraint provides idempotency —
 * duplicate webhook deliveries are safely rejected.
 */
@Entity
@Table(name = "movu_mission_events", indexes = {
        @Index(name = "idx_mme_mission_id", columnList = "mission_id"),
        @Index(name = "idx_mme_notification_type", columnList = "notification_type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovuMissionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Movu's unique notification ID. UNIQUE constraint = idempotency key. */
    @Column(name = "notification_id", nullable = false, unique = true, length = 100)
    private String notificationId;

    /** The mission this event belongs to. */
    @Column(name = "mission_id", nullable = false, length = 64)
    private String missionId;

    /**
     * The Movu notificationType (e.g. MissionCreated, MissionPicked,
     * MissionDropped).
     */
    @Column(name = "notification_type", nullable = false, length = 100)
    private String notificationType;

    /**
     * Vehicle that executed/is executing the mission (nullable for some events).
     */
    @Column(name = "vehicle_id", length = 20)
    private String vehicleId;

    /** Load identifier tracked by aSTC (nullable). */
    @Column(name = "load_id", length = 100)
    private String loadId;

    /** Raw JSON payload for audit trail and debugging. */
    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    /** Timestamp from the Movu aSTC (when the event actually occurred). */
    @Column(name = "event_timestamp")
    private Instant eventTimestamp;

    /** When PalletPilot processed this event. */
    @Column(name = "processed_at")
    private Instant processedAt;
}
