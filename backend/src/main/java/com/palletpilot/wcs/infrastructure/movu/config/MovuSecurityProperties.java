package com.palletpilot.wcs.infrastructure.movu.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Security configuration for Movu aSTC webhook endpoint.
 * Controls authentication, rate limiting, IP allowlist, and timestamp
 * validation.
 */
@Component
@ConfigurationProperties(prefix = "hardware.movu.webhook")
@Getter
@Setter
public class MovuSecurityProperties {

    /** Enable/disable webhook security. When false, all requests pass through. */
    private boolean enabled = true;

    /**
     * Maximum age (seconds) for webhook timestamp before rejection. Default 300s (5
     * min).
     */
    private int timestampMaxAgeSec = 300;

    /** Max requests per minute per source IP. Default 100. */
    private int maxRequestsPerMinute = 100;

    /**
     * IP allowlist in CIDR notation. If empty, all IPs allowed.
     * Example: ["192.168.1.0/24", "10.0.0.0/8"]
     */
    private List<String> allowedIps = List.of();
}
