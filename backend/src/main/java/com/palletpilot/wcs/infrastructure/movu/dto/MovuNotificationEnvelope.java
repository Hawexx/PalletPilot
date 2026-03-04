package com.palletpilot.wcs.infrastructure.movu.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Top-level envelope for Movu aSTC webhook notifications.
 * The actual payload is parsed separately into typed DTOs.
 *
 * Real Movu format:
 * {
 * "notificationType": "MissionDropped",
 * "payload": { "missionId": "21", "vehicleId": "1", ... },
 * "notificationId": "uuid-here",
 * "timeStamp": "2023-06-29T12:13:22Z",
 * "contextType": "Mission"
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MovuNotificationEnvelope {

    @JsonProperty("notificationType")
    private String notificationType;

    @JsonProperty("notificationId")
    @JsonAlias({ "NotificationId" })
    private String notificationId;

    @JsonProperty("timeStamp")
    @JsonAlias({ "TimeStamp", "timestamp" })
    private String timeStamp;

    @JsonProperty("contextType")
    @JsonAlias({ "ContextType" })
    private String contextType;

    /** The parsed mission payload. Set after parsing. */
    private MovuMissionPayload missionPayload;

    /** Whether this notification was detected as legacy flat format. */
    private boolean legacyFormat;

    /**
     * Equipment UUID resolved from webhook token by MovuWebhookAuthFilter.
     * Not part of the JSON payload — set programmatically by the controller.
     * May be null if security is disabled or no tokens are configured.
     */
    @Builder.Default
    private UUID resolvedEquipmentId = null;
}
