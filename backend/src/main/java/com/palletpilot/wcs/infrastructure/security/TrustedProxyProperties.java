package com.palletpilot.wcs.infrastructure.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for trusted reverse-proxy headers.
 *
 * security.use-forwarded-headers=false (dev default — ignore XFF)
 * security.trusted-proxies=10.0.0.0/8 (CIDR list of trusted proxies)
 */
@Component
@ConfigurationProperties(prefix = "security")
@Getter
@Setter
public class TrustedProxyProperties {

    /** Whether to honour X-Forwarded-For / Forwarded headers at all. */
    private boolean useForwardedHeaders = false;

    /** CIDR allowlist of trusted reverse proxies (e.g. "10.0.0.0/8"). */
    private List<String> trustedProxies = new ArrayList<>();
}
