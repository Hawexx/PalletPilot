package com.palletpilot.wcs.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Equipment capabilities model — loaded from Equipment.configJson.
 * Defines what commands an equipment can accept, its timeouts,
 * speed limits, and protocol-specific configuration.
 *
 * Not a JPA entity — parsed on demand from configJson.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class EquipmentCapabilities {

    // --- ADS / PLC Configuration ---
    private String adsNetId;
    private String symbolPath;

    // --- Timing ---
    /** Command timeout in milliseconds. Overrides default per equipment type. */
    private Integer timeoutMs;

    /** Poll interval in milliseconds (default: 200ms). */
    private Integer pollIntervalMs;

    // --- Capabilities ---
    /** Allowed command types, e.g. ["PICK", "DROP", "HOME"] */
    private List<String> capabilities;

    // --- KUKA-Specific ---
    private Double maxPayloadKg;
    private Integer reachMm;

    // --- Conveyor-Specific ---
    private Double maxSpeedMps;
    private Integer lengthMm;

    // --- Wrapper-Specific ---
    /** Supported wrap profiles, e.g. ["STANDARD", "HEAVY", "LIGHT"] */
    private List<String> profiles;
    private Integer maxHeightMm;

    // --- General ---
    /** Additional protocol-specific parameters */
    private Map<String, Object> extra;

    /**
     * Check if a command is allowed for this equipment.
     *
     * @param command the command to check (e.g., "PICK", "START")
     * @return true if allowed, or if capabilities list is null/empty (permissive
     *         default)
     */
    public boolean isCommandAllowed(String command) {
        if (capabilities == null || capabilities.isEmpty()) {
            return true; // Permissive: no restrictions if not configured
        }
        return capabilities.stream()
                .anyMatch(c -> c.equalsIgnoreCase(command));
    }

    /**
     * Get timeout with fallback to default.
     */
    public int getEffectiveTimeoutMs(int defaultTimeoutMs) {
        return timeoutMs != null && timeoutMs > 0 ? timeoutMs : defaultTimeoutMs;
    }

    /**
     * Get poll interval with fallback to default.
     */
    public int getEffectivePollIntervalMs(int defaultPollMs) {
        return pollIntervalMs != null && pollIntervalMs > 0 ? pollIntervalMs : defaultPollMs;
    }
}
