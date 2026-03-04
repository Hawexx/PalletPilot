package com.palletpilot.wcs.infrastructure.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for CorrelationIdFilter.MDC_KEY — used by GlobalExceptionHandler
 * and throughout the codebase for log correlation.
 */
class CorrelationIdFilterTest {

    @Test
    @DisplayName("MDC_KEY constant is defined and non-blank")
    void mdcKey_isDefined() {
        assertThat(CorrelationIdFilter.MDC_KEY).isNotBlank();
        assertThat(CorrelationIdFilter.MDC_KEY).isEqualTo("correlationId");
    }
}
