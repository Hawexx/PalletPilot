package com.palletpilot.wcs.domain.exception;

/**
 * Thrown when a requested resource (order, equipment, pallet, etc.) is not
 * found.
 * Maps to HTTP 404.
 */
public class ResourceNotFoundException extends RuntimeException {

    private final String resourceType;
    private final String identifier;

    public ResourceNotFoundException(String resourceType, Object identifier) {
        super(resourceType + " not found: " + identifier);
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
