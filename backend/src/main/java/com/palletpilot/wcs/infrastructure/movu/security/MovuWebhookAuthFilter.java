package com.palletpilot.wcs.infrastructure.movu.security;

import com.palletpilot.wcs.infrastructure.movu.config.MovuSecurityProperties;
import com.palletpilot.wcs.infrastructure.security.ClientIpResolver;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Security filter for Movu aSTC webhook endpoints.
 * Applied ONLY to /api/movu/callback* paths.
 *
 * Security layers (in order):
 * 1. IP allowlist check (if configured)
 * 2. Rate limiting (per source IP)
 * 3. Token authentication (X-Movu-Webhook-Token → equipmentId)
 *
 * Resolved equipmentId is set as request attribute for controller use.
 *
 * IMPORTANT SECURITY vs "ALWAYS 200" POLICY:
 * - Auth/IP/rate-limit failures → return 401/403/429 (protect the system)
 * - Auth OK → controller ALWAYS returns 200 (prevent aSTC re-buffering)
 */
@Component
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE + 10) // After CorrelationIdFilter
public class MovuWebhookAuthFilter extends OncePerRequestFilter {

    public static final String WEBHOOK_TOKEN_HEADER = "X-Movu-Webhook-Token";
    public static final String EQUIPMENT_ID_ATTR = "movu.resolved.equipmentId";

    private final MovuSecurityProperties securityProps;
    private final MovuWebhookTokenService tokenService;
    private final ClientIpResolver ipResolver;

    /**
     * Simple sliding-window rate limiter: IP → request count (reset every minute)
     */
    private final ConcurrentHashMap<String, RateBucket> rateBuckets = new ConcurrentHashMap<>();

    public MovuWebhookAuthFilter(MovuSecurityProperties securityProps,
            MovuWebhookTokenService tokenService,
            ClientIpResolver ipResolver,
            MeterRegistry meterRegistry) {
        this.securityProps = securityProps;
        this.tokenService = tokenService;
        this.ipResolver = ipResolver;

        // Micrometer gauge: track rate bucket map size for leak detection
        Gauge.builder("palletpilot.movu.webhook.rate_buckets.size", rateBuckets, ConcurrentHashMap::size)
                .description("Number of tracked webhook rate-limit buckets")
                .tag("component", "movu_webhook")
                .register(meterRegistry);
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        // Only filter webhook callback paths
        String path = request.getServletPath();
        return !path.startsWith("/api/movu/callback");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {

        // If security is disabled (dev mode), pass through
        if (!securityProps.isEnabled()) {
            log.debug("Movu webhook security disabled — passing through");
            chain.doFilter(request, response);
            return;
        }

        String clientIp = ipResolver.resolveClientIp(request);

        // === Layer 1: IP Allowlist ===
        if (!securityProps.getAllowedIps().isEmpty()) {
            if (!isIpAllowed(clientIp)) {
                log.warn("Movu webhook rejected: IP {} not in allowlist", clientIp);
                response.setStatus(HttpStatus.FORBIDDEN.value());
                response.getWriter().write("{\"error\":\"IP not allowed\"}");
                return;
            }
        }

        // === Layer 2: Rate Limiting ===
        if (!checkRateLimit(clientIp)) {
            log.warn("Movu webhook rate-limited: IP {} exceeded {} rpm",
                    clientIp, securityProps.getMaxRequestsPerMinute());
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", "60");
            response.getWriter().write("{\"error\":\"Rate limit exceeded\"}");
            return;
        }

        // === Layer 3: Token Authentication ===
        String token = request.getHeader(WEBHOOK_TOKEN_HEADER);

        // If no tokens are configured yet, allow through (first-time setup)
        if (!tokenService.hasConfiguredTokens()) {
            log.debug("No webhook tokens configured — passing through (setup mode)");
            chain.doFilter(request, response);
            return;
        }

        if (token == null || token.isBlank()) {
            log.warn("Movu webhook rejected: missing {} header from {}", WEBHOOK_TOKEN_HEADER, clientIp);
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.getWriter().write("{\"error\":\"Missing webhook token\"}");
            return;
        }

        var resolvedEquipmentId = tokenService.resolveEquipmentId(token);
        if (resolvedEquipmentId.isEmpty()) {
            log.warn("Movu webhook rejected: invalid token from {}", clientIp);
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.getWriter().write("{\"error\":\"Invalid webhook token\"}");
            return;
        }

        // === Auth OK — set resolved equipmentId for controller ===
        UUID equipmentId = resolvedEquipmentId.get();
        request.setAttribute(EQUIPMENT_ID_ATTR, equipmentId);
        log.debug("Movu webhook authenticated: equipmentId={} from {}", equipmentId, clientIp);

        // From here on, controller will ALWAYS return 200 (even if processing fails)
        chain.doFilter(request, response);
    }

    // ========================
    // Rate Limiting
    // ========================

    private boolean checkRateLimit(String clientIp) {
        long now = System.currentTimeMillis();
        RateBucket bucket = rateBuckets.compute(clientIp, (ip, existing) -> {
            if (existing == null || now - existing.windowStart > 60_000) {
                return new RateBucket(now, new AtomicInteger(1), Instant.now());
            }
            existing.count.incrementAndGet();
            existing.lastSeen = Instant.now();
            return existing;
        });
        return bucket.count.get() <= securityProps.getMaxRequestsPerMinute();
    }

    /**
     * Periodic cleanup: evicts IP entries inactive for more than 5 minutes.
     * Prevents unbounded memory growth from unique webhook source IPs.
     */
    @Scheduled(fixedRate = 300_000) // Every 5 minutes
    public void evictStaleRateBuckets() {
        if (rateBuckets.isEmpty())
            return;

        Instant threshold = Instant.now().minusSeconds(300);
        int before = rateBuckets.size();

        rateBuckets.entrySet().removeIf(entry -> entry.getValue().lastSeen.isBefore(threshold));

        int evicted = before - rateBuckets.size();
        if (evicted > 0) {
            log.info("[MOVU_AUTH] Evicted {} stale rate buckets, {} remaining", evicted, rateBuckets.size());
        }
    }

    private static class RateBucket {
        final long windowStart;
        final AtomicInteger count;
        volatile Instant lastSeen;

        RateBucket(long windowStart, AtomicInteger count, Instant lastSeen) {
            this.windowStart = windowStart;
            this.count = count;
            this.lastSeen = lastSeen;
        }
    }

    // ========================
    // IP Allowlist
    // ========================

    private boolean isIpAllowed(String clientIp) {
        try {
            InetAddress clientAddr = InetAddress.getByName(clientIp);
            for (String cidr : securityProps.getAllowedIps()) {
                if (matchesCidr(clientAddr, cidr))
                    return true;
            }
        } catch (Exception e) {
            log.warn("Failed to resolve client IP {}: {}", clientIp, e.getMessage());
        }
        return false;
    }

    private boolean matchesCidr(InetAddress clientAddr, String cidr) {
        try {
            String[] parts = cidr.split("/");
            InetAddress networkAddr = InetAddress.getByName(parts[0]);
            int prefixLen = parts.length > 1 ? Integer.parseInt(parts[1]) : 32;

            byte[] clientBytes = clientAddr.getAddress();
            byte[] networkBytes = networkAddr.getAddress();
            if (clientBytes.length != networkBytes.length)
                return false;

            int fullBytes = prefixLen / 8;
            int remainingBits = prefixLen % 8;

            for (int i = 0; i < fullBytes; i++) {
                if (clientBytes[i] != networkBytes[i])
                    return false;
            }
            if (remainingBits > 0 && fullBytes < clientBytes.length) {
                int mask = 0xFF << (8 - remainingBits);
                if ((clientBytes[fullBytes] & mask) != (networkBytes[fullBytes] & mask))
                    return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("Invalid CIDR: {}", cidr);
            return false;
        }
    }
}
