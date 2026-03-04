package com.palletpilot.wcs.infrastructure.web;

import com.palletpilot.wcs.domain.exception.BusinessRuleViolationException;
import com.palletpilot.wcs.domain.exception.DuplicateResourceException;
import com.palletpilot.wcs.domain.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Global exception handler for PalletPilot WCS REST API.
 *
 * Converts all exceptions to standardized {@link ApiErrorResponse} JSON:
 * - Domain exceptions → appropriate HTTP status
 * - Validation errors → 400 with field-level details
 * - Unknown errors → 500 with trace ID for log correlation
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ── Domain Exceptions ──────────────────────────────────────

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest req) {
        log.warn("[404] {} — {}", req.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", ex.getMessage(), req);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicate(DuplicateResourceException ex, HttpServletRequest req) {
        log.warn("[409] {} — {}", req.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE", ex.getMessage(), req);
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessRule(BusinessRuleViolationException ex,
            HttpServletRequest req) {
        String code = ex.getRuleCode() != null ? ex.getRuleCode() : "BUSINESS_RULE_VIOLATION";
        log.warn("[422] {} — [{}] {}", req.getRequestURI(), code, ex.getMessage());
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, code, ex.getMessage(), req);
    }

    // ── Validation / Input Errors ──────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex,
            HttpServletRequest req) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fe -> fieldErrors.put(fe.getField(), fe.getDefaultMessage()));

        log.warn("[400] Validation failed on {} — {} field error(s)", req.getRequestURI(), fieldErrors.size());

        ApiErrorResponse body = ApiErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .code("VALIDATION_FAILED")
                .message("Request validation failed")
                .path(req.getRequestURI())
                .traceId(generateTraceId())
                .fieldErrors(fieldErrors)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParam(MissingServletRequestParameterException ex,
            HttpServletRequest req) {
        log.warn("[400] Missing param on {} — {}", req.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER", ex.getMessage(), req);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
            HttpServletRequest req) {
        String msg = String.format("Parameter '%s' must be of type %s",
                ex.getName(), ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");
        log.warn("[400] Type mismatch on {} — {}", req.getRequestURI(), msg);
        return buildResponse(HttpStatus.BAD_REQUEST, "TYPE_MISMATCH", msg, req);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex,
            HttpServletRequest req) {
        log.warn("[400] Unreadable request body on {} — {}", req.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "MALFORMED_JSON", "Request body is not valid JSON", req);
    }

    // ── Standard Java Exceptions ───────────────────────────────

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest req) {
        log.warn("[400] {} — {}", req.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", ex.getMessage(), req);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalState(IllegalStateException ex, HttpServletRequest req) {
        log.warn("[409] {} — {}", req.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, "INVALID_STATE", ex.getMessage(), req);
    }

    // ── Catch-All ──────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(Exception ex, HttpServletRequest req) {
        String traceId = generateTraceId();
        log.error("[500] Unhandled exception on {} — traceId={}", req.getRequestURI(), traceId, ex);

        ApiErrorResponse body = ApiErrorResponse.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Internal Server Error")
                .code("INTERNAL_ERROR")
                .message("An unexpected error occurred. Reference: " + traceId)
                .path(req.getRequestURI())
                .traceId(traceId)
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    // ── Helper ─────────────────────────────────────────────────

    private ResponseEntity<ApiErrorResponse> buildResponse(HttpStatus status, String code, String message,
            HttpServletRequest req) {
        ApiErrorResponse body = ApiErrorResponse.builder()
                .status(status.value())
                .error(status.getReasonPhrase())
                .code(code)
                .message(message)
                .path(req.getRequestURI())
                .traceId(generateTraceId())
                .build();
        return ResponseEntity.status(status).body(body);
    }

    /**
     * Use the existing correlation ID from MDC (set by CorrelationIdFilter)
     * instead of generating a random one — ensures error responses correlate
     * with request logs for debugging.
     */
    private String generateTraceId() {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        return (correlationId != null && !correlationId.isBlank())
                ? correlationId
                : UUID.randomUUID().toString().substring(0, 8);
    }
}
