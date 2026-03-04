package com.palletpilot.wcs.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Structured API request/response logging interceptor.
 *
 * Logs every API call with: method, path, status, duration, correlation ID.
 * Excludes actuator, swagger, and websocket paths to reduce noise.
 *
 * Example output:
 * [API] GET /api/orders → 200 (45ms) [corr=f3a1b2c4]
 * [API] POST /api/orders → 201 (120ms) [corr=e7d8c9f0]
 * [API] GET /api/orders/abc → 404 (8ms) [corr=a1b2c3d4]
 */
@Component
@Slf4j
public class RequestLoggingInterceptor implements HandlerInterceptor {

    private static final String START_TIME_ATTR = "pp.request.startTime";

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler) {
        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler,
            Exception ex) {
        String path = request.getRequestURI();

        // Skip noisy paths
        if (shouldSkip(path)) {
            return;
        }

        long startTime = (long) request.getAttribute(START_TIME_ATTR);
        long duration = System.currentTimeMillis() - startTime;
        int status = response.getStatus();
        String method = request.getMethod();
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        String corrTag = correlationId != null ? " [corr=" + correlationId + "]" : "";

        // Query string for GET requests (useful for debugging)
        String query = request.getQueryString();
        String fullPath = query != null ? path + "?" + query : path;

        if (status >= 500) {
            log.error("[API] {} {} → {} ({}ms){}{}", method, fullPath, status, duration, corrTag,
                    ex != null ? " | error=" + ex.getMessage() : "");
        } else if (status >= 400) {
            log.warn("[API] {} {} → {} ({}ms){}", method, fullPath, status, duration, corrTag);
        } else {
            log.info("[API] {} {} → {} ({}ms){}", method, fullPath, status, duration, corrTag);
        }
    }

    private boolean shouldSkip(String path) {
        return path.startsWith("/actuator")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/ws")
                || path.equals("/favicon.ico");
    }
}
