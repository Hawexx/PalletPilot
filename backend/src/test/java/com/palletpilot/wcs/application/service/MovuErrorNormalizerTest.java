package com.palletpilot.wcs.application.service;

import com.palletpilot.wcs.application.service.MovuErrorNormalizer.MovuErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MovuErrorNormalizer}.
 * Validates that all aSTC error messages are correctly normalized
 * into standardized {@link MovuErrorCode} values.
 */
class MovuErrorNormalizerTest {

    private final MovuErrorNormalizer normalizer = new MovuErrorNormalizer();

    // ── LANE_FULL ──

    @Test
    @DisplayName("LANE_FULL — 'lane full' in error message")
    void testLaneFull_explicit() {
        assertThat(normalizer.normalize("Lane is full", null))
                .isEqualTo(MovuErrorCode.LANE_FULL);
    }

    @Test
    @DisplayName("LANE_FULL — 'lane occupied' variant")
    void testLaneFull_occupied() {
        assertThat(normalizer.normalize("Lane 3 is occupied", null))
                .isEqualTo(MovuErrorCode.LANE_FULL);
    }

    @Test
    @DisplayName("LANE_FULL — case insensitive")
    void testLaneFull_caseInsensitive() {
        assertThat(normalizer.normalize("LANE FULL ERROR", null))
                .isEqualTo(MovuErrorCode.LANE_FULL);
    }

    // ── NOT_IN_ZONE ──

    @Test
    @DisplayName("NOT_IN_ZONE — 'not in zone' literal")
    void testNotInZone_literal() {
        assertThat(normalizer.normalize("Vehicle not in zone", null))
                .isEqualTo(MovuErrorCode.NOT_IN_ZONE);
    }

    @Test
    @DisplayName("NOT_IN_ZONE — 'out of zone' variant")
    void testNotInZone_outOfZone() {
        assertThat(normalizer.normalize("Shuttle out of zone boundary", null))
                .isEqualTo(MovuErrorCode.NOT_IN_ZONE);
    }

    @Test
    @DisplayName("NOT_IN_ZONE — 'position' + 'error' combined (operator precedence test)")
    void testNotInZone_positionError() {
        // This is the critical test for the operator precedence fix
        assertThat(normalizer.normalize("position error detected", null))
                .isEqualTo(MovuErrorCode.NOT_IN_ZONE);
    }

    @Test
    @DisplayName("NOT_IN_ZONE — only 'position' without 'error' should NOT match")
    void testNotInZone_positionOnly_noMatch() {
        // "position" alone without "error" should fall through to UNKNOWN
        assertThat(normalizer.normalize("position updated", null))
                .isNotEqualTo(MovuErrorCode.NOT_IN_ZONE);
    }

    @Test
    @DisplayName("NOT_IN_ZONE — only 'error' without 'position' should NOT match NOT_IN_ZONE")
    void testNotInZone_errorOnly_noMatch() {
        // "error" alone without "position" should not match NOT_IN_ZONE
        // (may match VEHICLE_ERROR if "vehicle" is present, otherwise UNKNOWN)
        MovuErrorCode result = normalizer.normalize("generic error occurred", null);
        assertThat(result).isNotEqualTo(MovuErrorCode.NOT_IN_ZONE);
    }

    // ── VEHICLE_ERROR ──

    @Test
    @DisplayName("VEHICLE_ERROR — 'vehicle error'")
    void testVehicleError_explicit() {
        assertThat(normalizer.normalize("Vehicle error on shuttle 1", null))
                .isEqualTo(MovuErrorCode.VEHICLE_ERROR);
    }

    @Test
    @DisplayName("VEHICLE_ERROR — 'vehicle fault'")
    void testVehicleError_fault() {
        assertThat(normalizer.normalize("Vehicle fault detected", null))
                .isEqualTo(MovuErrorCode.VEHICLE_ERROR);
    }

    @Test
    @DisplayName("VEHICLE_ERROR — 'vehicle offline'")
    void testVehicleError_offline() {
        assertThat(normalizer.normalize("Vehicle 3 is offline", null))
                .isEqualTo(MovuErrorCode.VEHICLE_ERROR);
    }

    // ── COMMUNICATION_LOST ──

    @ParameterizedTest
    @CsvSource({
            "'communication failure', COMMUNICATION_LOST",
            "'connection timeout on aSTC', COMMUNICATION_LOST",
            "'timeout waiting for response', COMMUNICATION_LOST",
            "'device unreachable', COMMUNICATION_LOST"
    })
    @DisplayName("COMMUNICATION_LOST — various messages")
    void testCommunicationLost(String errorMessage, MovuErrorCode expected) {
        assertThat(normalizer.normalize(errorMessage, null))
                .isEqualTo(expected);
    }

    // ── UNKNOWN ──

    @Test
    @DisplayName("UNKNOWN — null inputs")
    void testUnknown_nullInputs() {
        assertThat(normalizer.normalize(null, null))
                .isEqualTo(MovuErrorCode.UNKNOWN);
    }

    @Test
    @DisplayName("UNKNOWN — unrecognized error message")
    void testUnknown_unrecognized() {
        assertThat(normalizer.normalize("something completely unexpected", null))
                .isEqualTo(MovuErrorCode.UNKNOWN);
    }

    // ── STATUS-BASED NORMALIZATION ──

    @Test
    @DisplayName("Error code derived from missionStatus when errorMessage is null")
    void testNormalize_fromStatus() {
        assertThat(normalizer.normalize(null, "lane full"))
                .isEqualTo(MovuErrorCode.LANE_FULL);
    }

    @Test
    @DisplayName("Error code derived from combined errorMessage + missionStatus")
    void testNormalize_combined() {
        // errorMessage has "vehicle", status has "error" — together they match
        // VEHICLE_ERROR
        assertThat(normalizer.normalize("vehicle issue", "error state"))
                .isEqualTo(MovuErrorCode.VEHICLE_ERROR);
    }
}
