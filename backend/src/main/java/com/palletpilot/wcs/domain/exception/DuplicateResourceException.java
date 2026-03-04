package com.palletpilot.wcs.domain.exception;

/**
 * Thrown when attempting to create a resource that already exists (duplicate
 * WMS ID, etc.).
 * Maps to HTTP 409 Conflict.
 */
public class DuplicateResourceException extends RuntimeException {

    private final String resourceType;
    private final String identifier;

    public DuplicateResourceException(String resourceType, Object identifier) {
        super(resourceType + " already exists: " + identifier);
        this.resourceType = resourceType;
        this.identifier = String.valueOf(identifier);
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getIdentifier() {
        return identifier;
    }
}
