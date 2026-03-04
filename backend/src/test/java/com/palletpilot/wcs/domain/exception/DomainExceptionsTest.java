package com.palletpilot.wcs.domain.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for domain exceptions — constructors, messages, and getters.
 */
class DomainExceptionsTest {

    // ── ResourceNotFoundException ──

    @Test
    @DisplayName("ResourceNotFoundException: message, resourceType, identifier")
    void resourceNotFound_constructorAndGetters() {
        UUID id = UUID.randomUUID();
        var ex = new ResourceNotFoundException("Order", id);

        assertThat(ex.getMessage()).contains("Order").contains(id.toString());
        assertThat(ex.getResourceType()).isEqualTo("Order");
        assertThat(ex.getIdentifier()).isEqualTo(id.toString());
    }

    @Test
    @DisplayName("ResourceNotFoundException: string identifier")
    void resourceNotFound_stringIdentifier() {
        var ex = new ResourceNotFoundException("Equipment", "aSTC-1");

        assertThat(ex.getIdentifier()).isEqualTo("aSTC-1");
        assertThat(ex.getMessage()).contains("not found");
    }

    // ── DuplicateResourceException ──

    @Test
    @DisplayName("DuplicateResourceException: message, resourceType, identifier")
    void duplicateResource_constructorAndGetters() {
        var ex = new DuplicateResourceException("WmsOrder", "WMS-123");

        assertThat(ex.getMessage()).contains("WmsOrder").contains("already exists");
        assertThat(ex.getResourceType()).isEqualTo("WmsOrder");
        assertThat(ex.getIdentifier()).isEqualTo("WMS-123");
    }

    // ── BusinessRuleViolationException ──

    @Test
    @DisplayName("BusinessRuleViolationException: message-only constructor")
    void businessRule_messageOnly() {
        var ex = new BusinessRuleViolationException("Warehouse is full");

        assertThat(ex.getMessage()).isEqualTo("Warehouse is full");
        assertThat(ex.getRuleCode()).isNull();
    }

    @Test
    @DisplayName("BusinessRuleViolationException: ruleCode + message constructor")
    void businessRule_ruleCodeAndMessage() {
        var ex = new BusinessRuleViolationException("WAREHOUSE_FULL", "No slots available");

        assertThat(ex.getRuleCode()).isEqualTo("WAREHOUSE_FULL");
        assertThat(ex.getMessage()).isEqualTo("No slots available");
    }
}
