package com.palletpilot.wcs.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Standardized API error response.
 * All error responses from PalletPilot WCS follow this contract.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiErrorResponse {

    /** ISO-8601 timestamp */
    @Builder.Default
    private final String timestamp = Instant.now().toString();

    /** HTTP status code */
    private final int status;

    /** HTTP status reason (e.g., "Bad Request") */
    private final String error;

    /** Human-readable error message */
    private final String message;

    /** Machine-readable error code for client-side handling */
    private final String code;

    /** Request path that caused the error */
    private final String path;

    /** Correlation/trace ID for log tracing */
    private final String traceId;

    /** Validation errors keyed by field name (for 422 responses) */
    private final Map<String, String> fieldErrors;

    /** Multiple error messages (for batch operations) */
    private final List<String> details;
}
