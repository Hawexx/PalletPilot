package com.palletpilot.wcs.infrastructure.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Optional API key authentication filter.
 * 
 * When enabled, requires X-API-Key header for all /api/** endpoints.
 * Certain paths (auth, swagger, actuator, websocket) are excluded.
 * 
 * Config:
 * app.api-key.enabled=true
 * app.api-key.key=your-secret-key
 */
@Component
@Order(2)
@Slf4j
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    @Value("${app.api-key.enabled:false}")
    private boolean enabled;

    @Value("${app.api-key.key:}")
    private String apiKey;

    private static final String HEADER_NAME = "X-API-Key";
    private static final Set<String> EXCLUDED_PREFIXES = Set.of(
            "/api/auth",
            "/swagger-ui",
            "/v3/api-docs",
            "/actuator",
            "/ws");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!enabled || apiKey.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();

        // Skip excluded paths
        if (EXCLUDED_PREFIXES.stream().anyMatch(path::startsWith)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Only enforce on /api/** paths
        if (!path.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String providedKey = request.getHeader(HEADER_NAME);
        if (providedKey == null || !providedKey.equals(apiKey)) {
            log.warn("[API-KEY] Unauthorized request to {} from {}", path, request.getRemoteAddr());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\":\"Unauthorized\",\"message\":\"Missing or invalid X-API-Key header\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
