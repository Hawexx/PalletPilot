package com.palletpilot.wcs.infrastructure.movu.security;

import com.palletpilot.wcs.infrastructure.movu.config.MovuSecurityProperties;
import com.palletpilot.wcs.infrastructure.security.ClientIpResolver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MovuWebhookAuthFilter — 3-layer security model.
 * Tests: path filtering, security disabled, IP allowlist + CIDR,
 * rate limiting, token auth, setup mode, stale eviction.
 */
@ExtendWith(MockitoExtension.class)
class MovuWebhookAuthFilterTest {

    @Mock
    private MovuWebhookTokenService tokenService;
    @Mock
    private ClientIpResolver ipResolver;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain chain;

    private MovuSecurityProperties securityProps;
    private MovuWebhookAuthFilter filter;

    @BeforeEach
    void setup() {
        securityProps = new MovuSecurityProperties();
        filter = new MovuWebhookAuthFilter(securityProps, tokenService, ipResolver, new SimpleMeterRegistry());
    }

    // ── Path Filtering ──

    @Test
    @DisplayName("shouldNotFilter: non-callback path → true (skip)")
    void shouldNotFilter_nonCallbackPath_skipFilter() {
        when(request.getServletPath()).thenReturn("/api/orders");
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    @DisplayName("shouldNotFilter: callback path → false (apply filter)")
    void shouldNotFilter_callbackPath_applyFilter() {
        when(request.getServletPath()).thenReturn("/api/movu/callback");
        assertThat(filter.shouldNotFilter(request)).isFalse();
    }

    // ── Security Disabled ──

    @Test
    @DisplayName("security disabled → passes through without checks")
    void securityDisabled_passesThrough() throws Exception {
        securityProps.setEnabled(false);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
    }

    // ── IP Allowlist ──

    @Test
    @DisplayName("IP not in allowlist → 403 Forbidden")
    void ipNotAllowed_403() throws Exception {
        securityProps.setEnabled(true);
        securityProps.setAllowedIps(List.of("10.0.0.0/8"));
        when(ipResolver.resolveClientIp(request)).thenReturn("192.168.1.100");
        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        filter.doFilterInternal(request, response, chain);

        verify(response).setStatus(403);
        verify(chain, never()).doFilter(any(), any());
        assertThat(sw.toString()).contains("IP not allowed");
    }

    @Test
    @DisplayName("IP in CIDR allowlist → passes to next layer")
    void ipInCidr_passes() throws Exception {
        securityProps.setEnabled(true);
        securityProps.setAllowedIps(List.of("192.168.1.0/24"));
        securityProps.setMaxRequestsPerMinute(100);
        when(ipResolver.resolveClientIp(request)).thenReturn("192.168.1.50");
        // No tokens configured → setup mode passthrough
        when(tokenService.hasConfiguredTokens()).thenReturn(false);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    // ── Rate Limiting ──

    @Test
    @DisplayName("exceeds rate limit → 429 Too Many Requests")
    void exceedsRateLimit_429() throws Exception {
        securityProps.setEnabled(true);
        securityProps.setMaxRequestsPerMinute(2);
        when(ipResolver.resolveClientIp(request)).thenReturn("1.2.3.4");
        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        // First 2 requests should pass (need token service for chain)
        when(tokenService.hasConfiguredTokens()).thenReturn(false);
        filter.doFilterInternal(request, response, chain);
        filter.doFilterInternal(request, response, chain);

        // 3rd should be rate-limited
        filter.doFilterInternal(request, response, chain);

        verify(response).setStatus(429);
    }

    // ── Token Authentication ──

    @Test
    @DisplayName("missing token header → 401 Unauthorized")
    void missingToken_401() throws Exception {
        securityProps.setEnabled(true);
        securityProps.setMaxRequestsPerMinute(100);
        when(ipResolver.resolveClientIp(request)).thenReturn("10.0.0.1");
        when(tokenService.hasConfiguredTokens()).thenReturn(true);
        when(request.getHeader(MovuWebhookAuthFilter.WEBHOOK_TOKEN_HEADER)).thenReturn(null);
        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        filter.doFilterInternal(request, response, chain);

        verify(response).setStatus(401);
        assertThat(sw.toString()).contains("Missing webhook token");
    }

    @Test
    @DisplayName("invalid token → 401 Unauthorized")
    void invalidToken_401() throws Exception {
        securityProps.setEnabled(true);
        securityProps.setMaxRequestsPerMinute(100);
        when(ipResolver.resolveClientIp(request)).thenReturn("10.0.0.1");
        when(tokenService.hasConfiguredTokens()).thenReturn(true);
        when(request.getHeader(MovuWebhookAuthFilter.WEBHOOK_TOKEN_HEADER)).thenReturn("bad-token");
        when(tokenService.resolveEquipmentId("bad-token")).thenReturn(Optional.empty());
        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        filter.doFilterInternal(request, response, chain);

        verify(response).setStatus(401);
        assertThat(sw.toString()).contains("Invalid webhook token");
    }

    @Test
    @DisplayName("valid token → equipmentId set as request attribute, chain continues")
    void validToken_setsEquipmentId() throws Exception {
        UUID eqId = UUID.randomUUID();
        securityProps.setEnabled(true);
        securityProps.setMaxRequestsPerMinute(100);
        when(ipResolver.resolveClientIp(request)).thenReturn("10.0.0.1");
        when(tokenService.hasConfiguredTokens()).thenReturn(true);
        when(request.getHeader(MovuWebhookAuthFilter.WEBHOOK_TOKEN_HEADER)).thenReturn("valid-secret");
        when(tokenService.resolveEquipmentId("valid-secret")).thenReturn(Optional.of(eqId));

        filter.doFilterInternal(request, response, chain);

        verify(request).setAttribute(MovuWebhookAuthFilter.EQUIPMENT_ID_ATTR, eqId);
        verify(chain).doFilter(request, response);
    }

    // ── Stale Eviction ──

    @Test
    @DisplayName("evictStaleRateBuckets: does not throw on empty map")
    void evictStale_emptyMap_noException() {
        assertThatCode(() -> filter.evictStaleRateBuckets()).doesNotThrowAnyException();
    }
}
