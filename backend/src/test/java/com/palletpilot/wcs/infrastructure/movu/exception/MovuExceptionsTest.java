package com.palletpilot.wcs.infrastructure.movu.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for MovuExceptions — typed exception hierarchy.
 * Verifies constructors, messages, httpStatus, and cause propagation.
 */
class MovuExceptionsTest {

    @Test
    @DisplayName("MovuTransientException: with httpStatus")
    void transient_withHttpStatus() {
        var ex = new MovuExceptions.MovuTransientException("Server error on GET /missions", 503);

        assertThat(ex.getMessage()).contains("Server error");
        assertThat(ex.getHttpStatus()).isEqualTo(503);
    }

    @Test
    @DisplayName("MovuTransientException: with cause (httpStatus defaults to 0)")
    void transient_withCause() {
        var cause = new java.net.SocketTimeoutException("Read timed out");
        var ex = new MovuExceptions.MovuTransientException("Connection timeout", cause);

        assertThat(ex.getMessage()).contains("timeout");
        assertThat(ex.getCause()).isEqualTo(cause);
        assertThat(ex.getHttpStatus()).isEqualTo(0);
    }

    @Test
    @DisplayName("MovuUnavailableException: circuit breaker open")
    void unavailable_circuitOpen() {
        var ex = new MovuExceptions.MovuUnavailableException("Circuit breaker OPEN for aSTC-1");

        assertThat(ex.getMessage()).contains("Circuit breaker OPEN");
    }

    @Test
    @DisplayName("MovuUnavailableException: with cause")
    void unavailable_withCause() {
        var cause = new RuntimeException("breaker tripped");
        var ex = new MovuExceptions.MovuUnavailableException("Unavailable", cause);

        assertThat(ex.getCause()).isEqualTo(cause);
    }

    @Test
    @DisplayName("MovuAuthException: 401/403")
    void auth_unauthorized() {
        var ex = new MovuExceptions.MovuAuthException("401 Unauthorized for aSTC-1");

        assertThat(ex.getMessage()).contains("401");
    }

    @Test
    @DisplayName("MovuValidationException: 400 with response body")
    void validation_withResponseBody() {
        var ex = new MovuExceptions.MovuValidationException(
                "Invalid mission payload", "{\"error\":\"missing field 'lane'\"}");

        assertThat(ex.getMessage()).contains("Invalid mission");
        assertThat(ex.getResponseBody()).contains("missing field");
    }

    @Test
    @DisplayName("MovuNotFoundException: 404")
    void notFound_mission() {
        var ex = new MovuExceptions.MovuNotFoundException("Mission M-123 not found on aSTC");

        assertThat(ex.getMessage()).contains("M-123");
    }
}
