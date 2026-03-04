package com.palletpilot.wcs.application.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Normalizes Movu aSTC error messages into standardized error codes.
 * Used by MovuRemediationService to determine the correct remediation strategy.
 */
@Service
@Slf4j
public class MovuErrorNormalizer {

    public enum MovuErrorCode {
        LANE_FULL,
        NOT_IN_ZONE,
        VEHICLE_ERROR,
        COMMUNICATION_LOST,
        UNKNOWN
    }

    /**
     * Parse an aSTC error message/status into a normalized error code.
     */
    public MovuErrorCode normalize(String errorMessage, String missionStatus) {
        if (errorMessage == null && missionStatus == null)
            return MovuErrorCode.UNKNOWN;

        String combined = ((errorMessage != null ? errorMessage : "") + " " +
                (missionStatus != null ? missionStatus : "")).toLowerCase();

        if (combined.contains("lane") && (combined.contains("full") || combined.contains("occupied"))) {
            return MovuErrorCode.LANE_FULL;
        }
        if (combined.contains("not in zone") || combined.contains("out of zone") ||
                (combined.contains("position") && combined.contains("error"))) {
            return MovuErrorCode.NOT_IN_ZONE;
        }
        if (combined.contains("vehicle") && (combined.contains("error") || combined.contains("fault") ||
                combined.contains("offline"))) {
            return MovuErrorCode.VEHICLE_ERROR;
        }
        if (combined.contains("communication") || combined.contains("timeout") ||
                combined.contains("connection") || combined.contains("unreachable")) {
            return MovuErrorCode.COMMUNICATION_LOST;
        }

        return MovuErrorCode.UNKNOWN;
    }
}
