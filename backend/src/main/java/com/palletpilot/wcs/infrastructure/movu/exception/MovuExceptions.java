package com.palletpilot.wcs.infrastructure.movu.exception;

/**
 * Typed exception hierarchy for Movu aSTC communication failures.
 * Each type maps to a specific retry/recovery strategy.
 */
public class MovuExceptions {

    private MovuExceptions() {
    } // Utility class

    /**
     * Transient failure: timeouts, 5xx, connection refused.
     * RETRYABLE for idempotent operations.
     */
    public static class MovuTransientException extends RuntimeException {
        private final int httpStatus;

        public MovuTransientException(String message, int httpStatus) {
            super(message);
            this.httpStatus = httpStatus;
        }

        public MovuTransientException(String message, Throwable cause) {
            super(message, cause);
            this.httpStatus = 0;
        }

        public int getHttpStatus() {
            return httpStatus;
        }
    }

    /**
     * Circuit breaker is OPEN — fail fast, do not even attempt.
     * NOT retryable (breaker must close first).
     */
    public static class MovuUnavailableException extends RuntimeException {
        public MovuUnavailableException(String message) {
            super(message);
        }

        public MovuUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Authentication/authorization failure (401/403).
     * NOT retryable — check API key config.
     */
    public static class MovuAuthException extends RuntimeException {
        public MovuAuthException(String message) {
            super(message);
        }
    }

    /**
     * Validation error (400). Payload is malformed.
     * NOT retryable — fix the request.
     */
    public static class MovuValidationException extends RuntimeException {
        private final String responseBody;

        public MovuValidationException(String message, String responseBody) {
            super(message);
            this.responseBody = responseBody;
        }

        public String getResponseBody() {
            return responseBody;
        }
    }

    /**
     * Resource not found (404). Mission/vehicle doesn't exist.
     * NOT retryable.
     */
    public static class MovuNotFoundException extends RuntimeException {
        public MovuNotFoundException(String message) {
            super(message);
        }
    }
}
