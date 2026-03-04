package com.palletpilot.wcs.infrastructure.movu.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Externalized configuration for Movu aSTC HTTP client.
 * Bound from application.properties prefix "hardware.movu".
 *
 * Precedence: Equipment.configJson > SystemConfig DB > these defaults.
 */
@Component
@ConfigurationProperties(prefix = "hardware.movu")
@Getter
@Setter
public class MovuHttpProperties {

    // --- Connection ---
    private String baseUrl = "http://localhost:9000";
    private String apiKey = "YOUR_API_KEY_HERE";

    // --- Timeouts (ms) ---
    private int connectTimeoutMs = 3000;
    private int readTimeoutMs = 5000;

    // --- Connection Pool ---
    private int poolMaxTotal = 20;
    private int poolMaxPerRoute = 10;
    private int poolConnectionTtlSeconds = 300;
    private int poolIdleTimeoutSeconds = 60;

    // --- Retry ---
    private int retryMaxAttempts = 3;
    private int retryInitialBackoffMs = 250;
    private int retryMaxBackoffMs = 2000;
    private int retryJitterMs = 200;

    // --- Circuit Breaker ---
    private int breakerFailureRateThreshold = 50;
    private int breakerSlowCallDurationMs = 3000;
    private int breakerSlowCallRateThreshold = 80;
    private int breakerSlidingWindowSize = 10;
    private int breakerOpenStateDurationSeconds = 30;
    private int breakerPermittedCallsInHalfOpen = 3;
}
