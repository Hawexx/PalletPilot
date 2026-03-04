package com.palletpilot.wcs.domain.exception;

/**
 * Thrown when a business rule is violated (e.g., warehouse full, invalid state
 * transition).
 * Maps to HTTP 422 Unprocessable Entity.
 */
public class BusinessRuleViolationException extends RuntimeException {

    private final String ruleCode;

    public BusinessRuleViolationException(String message) {
        super(message);
        this.ruleCode = null;
    }

    public BusinessRuleViolationException(String ruleCode, String message) {
        super(message);
        this.ruleCode = ruleCode;
    }

    public String getRuleCode() {
        return ruleCode;
    }
}
