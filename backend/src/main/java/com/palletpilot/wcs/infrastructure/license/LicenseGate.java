package com.palletpilot.wcs.infrastructure.license;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Source-level override of license-core.jar's LicenseGate.
 * Takes classpath priority over the JAR version.
 *
 * In development mode (LICENSE_SKIP_CHECK=true), all traffic is allowed.
 * In production mode, traffic is gated by license validation state.
 */
@Component
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class LicenseGate extends OncePerRequestFilter {

    private static final AtomicBoolean LICENSED = new AtomicBoolean(
            "true".equalsIgnoreCase(System.getenv("LICENSE_SKIP_CHECK")));

    public static void activate() {
        LICENSED.set(true);
        log.info("LicenseGate: HTTP traffic unlocked");
    }

    public static void deactivate() {
        LICENSED.set(false);
        log.error("LicenseGate: HTTP traffic BLOCKED — license revoked");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {

        if (LICENSED.get()) {
            chain.doFilter(request, response);
        } else {
            response.setStatus(503);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Service unavailable\",\"reason\":\"License not validated\"}");
        }
    }
}
