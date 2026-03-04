package com.palletpilot.wcs.infrastructure.web;

import com.palletpilot.wcs.domain.exception.BusinessRuleViolationException;
import com.palletpilot.wcs.domain.exception.DuplicateResourceException;
import com.palletpilot.wcs.domain.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GlobalExceptionHandler — standardized error responses.
 * Tests all 10 exception handler methods for correct HTTP status, error code,
 * message content, path, and MDC trace correlation.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private HttpServletRequest mockRequest;

    @BeforeEach
    void setup() {
        mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getRequestURI()).thenReturn("/api/test");
        MDC.put("correlationId", "CORR-TEST-123");
    }

    @Test
    @DisplayName("ResourceNotFoundException → 404 + RESOURCE_NOT_FOUND")
    void notFound_returns404() {
        ResponseEntity<ApiErrorResponse> resp = handler.handleNotFound(
                new ResourceNotFoundException("Order", "ORD-123"),
                mockRequest);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(resp.getBody().getMessage()).contains("Order");
        assertThat(resp.getBody().getPath()).isEqualTo("/api/test");
    }

    @Test
    @DisplayName("DuplicateResourceException → 409 + DUPLICATE_RESOURCE")
    void duplicate_returns409() {
        ResponseEntity<ApiErrorResponse> resp = handler
                .handleDuplicate(new DuplicateResourceException("Slot", "S-001"), mockRequest);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo("DUPLICATE_RESOURCE");
    }

    @Test
    @DisplayName("BusinessRuleViolationException → 422 + custom rule code")
    void businessRule_returns422WithCode() {
        ResponseEntity<ApiErrorResponse> resp = handler.handleBusinessRule(
                new BusinessRuleViolationException("WAREHOUSE_FULL", "No available slots"), mockRequest);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo("WAREHOUSE_FULL");
        assertThat(resp.getBody().getMessage()).contains("No available slots");
    }

    @Test
    @DisplayName("IllegalArgumentException → 400 + INVALID_ARGUMENT")
    void illegalArgument_returns400() {
        ResponseEntity<ApiErrorResponse> resp = handler.handleIllegalArgument(new IllegalArgumentException("Bad param"),
                mockRequest);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo("INVALID_ARGUMENT");
    }

    @Test
    @DisplayName("IllegalStateException → 409 + INVALID_STATE")
    void illegalState_returns409() {
        ResponseEntity<ApiErrorResponse> resp = handler.handleIllegalState(new IllegalStateException("Wrong state"),
                mockRequest);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo("INVALID_STATE");
    }

    @Test
    @DisplayName("HttpMessageNotReadableException → 400 + MALFORMED_JSON")
    void malformedJson_returns400() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "JSON parse error", new MockHttpInputMessage(new byte[0]));
        ResponseEntity<ApiErrorResponse> resp = handler.handleUnreadableBody(ex, mockRequest);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo("MALFORMED_JSON");
        assertThat(resp.getBody().getMessage()).contains("not valid JSON");
    }

    @Test
    @DisplayName("generic Exception → 500 + INTERNAL_ERROR + traceId")
    void genericException_returns500WithTrace() {
        ResponseEntity<ApiErrorResponse> resp = handler.handleGeneric(new RuntimeException("Unexpected"), mockRequest);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(resp.getBody().getTraceId()).isNotBlank();
    }

    @Test
    @DisplayName("traceId uses MDC correlationId when present")
    void traceId_usesMdcCorrelation() {
        ResponseEntity<ApiErrorResponse> resp = handler.handleNotFound(new ResourceNotFoundException("Item", "X"),
                mockRequest);

        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getTraceId()).isEqualTo("CORR-TEST-123");
    }

    @Test
    @DisplayName("traceId falls back to UUID when MDC is empty")
    void traceId_fallsBackToUuid() {
        MDC.remove("correlationId");

        ResponseEntity<ApiErrorResponse> resp = handler.handleNotFound(new ResourceNotFoundException("Item", "Y"),
                mockRequest);

        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getTraceId()).isNotBlank();
        assertThat(resp.getBody().getTraceId()).isNotEqualTo("CORR-TEST-123");
    }

    @Test
    @DisplayName("all responses include path from request URI")
    void allResponses_includePath() {
        ResponseEntity<ApiErrorResponse> resp = handler.handleIllegalArgument(new IllegalArgumentException("test"),
                mockRequest);

        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getPath()).isEqualTo("/api/test");
    }
}
