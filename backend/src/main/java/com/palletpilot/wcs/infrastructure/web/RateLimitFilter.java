package com.palletpilot.wcs.infrastructure.web;

import com.palletpilot.wcs.infrastructure.security.ClientIpResolver;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IP-based API rate limiting filter.
 *
 * Uses a sliding window counter per IP address.
 * Returns 429 Too Many Requests with Retry-After header when limit is exceeded.
 *
 * Includes periodic cleanup to prevent memory leaks from stale IP entries.
 *
 * Configuration:
 * app.rate-limit.enabled=true
 * app.rate-limit.requests-per-minute=100
 */
@Component
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE + 5) // After CorrelationIdFilter, before business logic
public class RateLimitFilter extends OncePerRequestFilter {

    @Value("${app.rate-limit.enabled:true}")
    private boolean enabled;

    @Value("${app.rate-limit.requests-per-minute:100}")
    private int maxRequestsPerMinute;

    private final Map<String, ClientWindow> windows = new ConcurrentHashMap<>();
    private final ClientIpResolver ipResolver;

    /** Stale entry TTL: 5 minutes of inactivity → evict */
    private static final long EVICTION_TTL_MS = 5 * 60 * 1000L;

    public RateLimitFilter(ClientIpResolver ipResolver, MeterRegistry meterRegistry) {
        this.ipResolver = ipResolver;

        // Micrometer gauge: track in-memory map size for leak detection
        Gauge.builder("palletpilot.ratelimit.windows.size", windows, Map::size)
                .description("Number of tracked IP rate-limit windows")
                .tag("component", "ratelimit")
                .register(meterRegistry);
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        if (!enabled)
            return true;
        String path = request.getRequestURI();
        return path.startsWith("/actuator")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/ws")
                || path.equals("/favicon.ico");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {
        String clientIp = ipResolver.resolveClientIp(request);
        ClientWindow window = windows.computeIfAbsent(clientIp, k -> new ClientWindow());

        if (!window.tryAcquire(maxRequestsPerMinute)) {
            log.warn("[RATE_LIMIT] IP {} exceeded {}/min on {} {}",
                    clientIp, maxRequestsPerMinute, request.getMethod(), request.getRequestURI());

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", "60");
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"status\":429,\"error\":\"Too Many Requests\","
                            + "\"message\":\"Rate limit exceeded. Max " + maxRequestsPerMinute + " requests/minute.\","
                            + "\"path\":\"" + request.getRequestURI() + "\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * Periodic cleanup: evicts IP entries inactive for more than 5 minutes.
     * Prevents unbounded memory growth from unique client IPs.
     */
    @Scheduled(fixedRate = 300_000) // Every 5 minutes
    public void evictStaleEntries() {
        if (windows.isEmpty())
            return;

        long now = System.currentTimeMillis();
        int before = windows.size();

        windows.entrySet().removeIf(entry -> now - entry.getValue().getLastActivity() > EVICTION_TTL_MS);

        int evicted = before - windows.size();
        if (evicted > 0) {
            log.info("[RATE_LIMIT] Evicted {} stale IP entries, {} remaining", evicted, windows.size());
        }
    }

    /**
     * Sliding window counter — resets every minute.
     * Thread-safe via synchronized block (atomic reset of windowStart + count).
     */
    static class ClientWindow {
        private int count = 0;
        private long windowStart = System.currentTimeMillis();
        private long lastActivity = System.currentTimeMillis();

        synchronized boolean tryAcquire(int maxRequests) {
            long now = System.currentTimeMillis();
            lastActivity = now;

            // Reset window if > 60 seconds elapsed
            if (now - windowStart > 60_000) {
                windowStart = now;
                count = 1;
                return true;
            }

            return ++count <= maxRequests;
        }

        synchronized long getLastActivity() {
            return lastActivity;
        }
    }
}
