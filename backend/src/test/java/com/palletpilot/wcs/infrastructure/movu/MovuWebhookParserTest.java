package com.palletpilot.wcs.infrastructure.movu;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.palletpilot.wcs.infrastructure.movu.dto.MovuNotificationEnvelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for MovuWebhookParser.
 * Tests dual-format parsing (envelope vs legacy), PascalCase/camelCase
 * handling,
 * edge cases, and error conditions.
 */
class MovuWebhookParserTest {

    private MovuWebhookParser parser;

    @BeforeEach
    void setUp() {
        parser = new MovuWebhookParser(new ObjectMapper());
    }

    // ── Envelope Format ──

    @Nested
    @DisplayName("Envelope Format (Real aSTC)")
    class EnvelopeFormat {

        @Test
        @DisplayName("parses full aSTC envelope with nested payload")
        void parse_fullEnvelope() {
            Map<String, Object> payload = Map.of(
                    "notificationType", "MissionDropped",
                    "notificationId", "abc-123",
                    "timeStamp", "2026-01-15T10:30:00Z",
                    "contextType", "Mission",
                    "payload", Map.of("missionId", "M-42", "vehicleId", "V-1"));

            MovuNotificationEnvelope result = parser.parse(payload);

            assertThat(result.getNotificationType()).isEqualTo("MissionDropped");
            assertThat(result.getNotificationId()).isEqualTo("abc-123");
            assertThat(result.getTimeStamp()).isEqualTo("2026-01-15T10:30:00Z");
            assertThat(result.getContextType()).isEqualTo("Mission");
            assertThat(result.isLegacyFormat()).isFalse();
            assertThat(result.getMissionPayload()).isNotNull();
            assertThat(result.getMissionPayload().getMissionId()).isEqualTo("M-42");
            assertThat(result.getMissionPayload().getVehicleId()).isEqualTo("V-1");
        }

        @Test
        @DisplayName("handles PascalCase keys (Movu v1.0)")
        void parse_pascalCaseKeys() {
            Map<String, Object> payload = Map.of(
                    "NotificationType", "MissionPicked",
                    "NotificationId", "notif-456",
                    "TimeStamp", "2026-01-15T10:30:00Z");

            MovuNotificationEnvelope result = parser.parse(payload);

            assertThat(result.getNotificationType()).isEqualTo("MissionPicked");
            assertThat(result.getNotificationId()).isEqualTo("notif-456");
        }

        @Test
        @DisplayName("generates notificationId when missing")
        void parse_missingNotificationId_generatesOne() {
            Map<String, Object> payload = Map.of(
                    "notificationType", "MissionError");

            MovuNotificationEnvelope result = parser.parse(payload);

            assertThat(result.getNotificationId()).startsWith("generated-");
        }

        @Test
        @DisplayName("handles envelope with null payload")
        void parse_nullPayload_returnsEmptyMissionPayload() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("notificationType", "MissionCancelled");
            payload.put("payload", null);

            MovuNotificationEnvelope result = parser.parse(payload);

            assertThat(result.getMissionPayload()).isNotNull();
            assertThat(result.getMissionPayload().getMissionId()).isNull();
        }
    }

    // ── Legacy Flat Format ──

    @Nested
    @DisplayName("Legacy Flat Format (Backward Compatibility)")
    class LegacyFormat {

        @Test
        @DisplayName("parses legacy flat format with missionId + status")
        void parse_legacyFlat() {
            Map<String, Object> payload = Map.of(
                    "missionId", "M-99",
                    "status", "COMPLETED");

            MovuNotificationEnvelope result = parser.parse(payload);

            assertThat(result.isLegacyFormat()).isTrue();
            assertThat(result.getNotificationType()).isEqualTo("MissionDropped"); // COMPLETED → MissionDropped
            assertThat(result.getMissionPayload().getMissionId()).isEqualTo("M-99");
            assertThat(result.getNotificationId()).startsWith("legacy-");
        }

        @Test
        @DisplayName("maps FAILED status to MissionError")
        void parse_legacyFailed_mapsMissionError() {
            Map<String, Object> payload = Map.of(
                    "missionId", "M-50",
                    "status", "FAILED");

            MovuNotificationEnvelope result = parser.parse(payload);

            assertThat(result.getNotificationType()).isEqualTo("MissionError");
        }

        @Test
        @DisplayName("throws on legacy payload missing missionId")
        void parse_legacyMissingMissionId_throws() {
            Map<String, Object> payload = Map.of("status", "COMPLETED");

            assertThatThrownBy(() -> parser.parse(payload))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("missionId");
        }

        @Test
        @DisplayName("maps PICKED → MissionPicked")
        void parse_legacyPicked() {
            Map<String, Object> payload = Map.of("missionId", "M-1", "status", "PICKED");
            assertThat(parser.parse(payload).getNotificationType()).isEqualTo("MissionPicked");
        }

        @Test
        @DisplayName("maps CANCELLED → MissionCancelled")
        void parse_legacyCancelled() {
            Map<String, Object> payload = Map.of("missionId", "M-2", "status", "CANCELLED");
            assertThat(parser.parse(payload).getNotificationType()).isEqualTo("MissionCancelled");
        }

        @Test
        @DisplayName("maps ABORTED → MissionAborted")
        void parse_legacyAborted() {
            Map<String, Object> payload = Map.of("missionId", "M-3", "status", "ABORTED");
            assertThat(parser.parse(payload).getNotificationType()).isEqualTo("MissionAborted");
        }

        @Test
        @DisplayName("maps CREATED → MissionCreated")
        void parse_legacyCreated() {
            Map<String, Object> payload = Map.of("missionId", "M-4", "status", "CREATED");
            assertThat(parser.parse(payload).getNotificationType()).isEqualTo("MissionCreated");
        }

        @Test
        @DisplayName("maps RUNNING → MissionAssigned")
        void parse_legacyRunning() {
            Map<String, Object> payload = Map.of("missionId", "M-5", "status", "RUNNING");
            assertThat(parser.parse(payload).getNotificationType()).isEqualTo("MissionAssigned");
        }

        @Test
        @DisplayName("maps unknown status → MissionAssigned (default fallback)")
        void parse_legacyUnknown_defaultFallback() {
            Map<String, Object> payload = Map.of("missionId", "M-6", "status", "SOME_FUTURE_STATUS");
            assertThat(parser.parse(payload).getNotificationType()).isEqualTo("MissionAssigned");
        }
    }

    // ── Error Cases ──

    @Test
    @DisplayName("throws on null payload")
    void parse_null_throws() {
        assertThatThrownBy(() -> parser.parse(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Empty");
    }

    @Test
    @DisplayName("throws on empty payload")
    void parse_empty_throws() {
        assertThatThrownBy(() -> parser.parse(Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Empty");
    }
}
