package com.palletpilot.wcs.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ClientIpResolver.
 * Validates trusted proxy logic, CIDR matching, and XFF hardening.
 */
class ClientIpResolverTest {

    private TrustedProxyProperties props;
    private ClientIpResolver resolver;

    @BeforeEach
    void setUp() {
        props = new TrustedProxyProperties();
        resolver = new ClientIpResolver(props);
    }

    @Test
    @DisplayName("no forwarded headers → returns remoteAddr")
    void noForwardedHeaders_returnsRemoteAddr() {
        props.setUseForwardedHeaders(false);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("192.168.1.100");
        req.addHeader("X-Forwarded-For", "1.2.3.4");

        assertThat(resolver.resolveClientIp(req)).isEqualTo("192.168.1.100");
    }

    @Test
    @DisplayName("forwarded headers enabled but remote not trusted → returns remoteAddr")
    void untrustedProxy_returnsRemoteAddr() {
        props.setUseForwardedHeaders(true);
        props.setTrustedProxies(List.of("10.0.0.0/8"));

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("192.168.1.100"); // Not in 10.0.0.0/8
        req.addHeader("X-Forwarded-For", "1.2.3.4");

        assertThat(resolver.resolveClientIp(req)).isEqualTo("192.168.1.100");
    }

    @Test
    @DisplayName("trusted proxy + single XFF → returns XFF IP")
    void trustedProxy_singleXff_returnsXffIp() {
        props.setUseForwardedHeaders(true);
        props.setTrustedProxies(List.of("10.0.0.0/8"));

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.1.5"); // In 10.0.0.0/8
        req.addHeader("X-Forwarded-For", "203.0.113.50");

        assertThat(resolver.resolveClientIp(req)).isEqualTo("203.0.113.50");
    }

    @Test
    @DisplayName("trusted proxy + multiple XFF → returns first (leftmost) IP")
    void trustedProxy_multipleXff_returnsFirst() {
        props.setUseForwardedHeaders(true);
        props.setTrustedProxies(List.of("172.16.0.0/12"));

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("172.20.0.1"); // In 172.16.0.0/12
        req.addHeader("X-Forwarded-For", "203.0.113.50, 172.20.0.1, 10.0.0.1");

        assertThat(resolver.resolveClientIp(req)).isEqualTo("203.0.113.50");
    }

    @Test
    @DisplayName("invalid XFF value → falls back to remoteAddr")
    void invalidXff_fallsBack() {
        props.setUseForwardedHeaders(true);
        props.setTrustedProxies(List.of("10.0.0.0/8"));

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.1.5");
        req.addHeader("X-Forwarded-For", "not-an-ip");

        assertThat(resolver.resolveClientIp(req)).isEqualTo("10.0.1.5");
    }

    @Test
    @DisplayName("no XFF header present with trusted proxy → returns remoteAddr")
    void trustedProxy_noXff_returnsRemoteAddr() {
        props.setUseForwardedHeaders(true);
        props.setTrustedProxies(List.of("10.0.0.0/8"));

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.1.5");
        // No XFF header

        assertThat(resolver.resolveClientIp(req)).isEqualTo("10.0.1.5");
    }

    // ── CIDR Matching ──

    @Test
    @DisplayName("CIDR match: exact /32")
    void cidrMatch_exact32() throws Exception {
        assertThat(ClientIpResolver.matchesCidr(
                java.net.InetAddress.getByName("10.0.0.1"), "10.0.0.1/32")).isTrue();
        assertThat(ClientIpResolver.matchesCidr(
                java.net.InetAddress.getByName("10.0.0.2"), "10.0.0.1/32")).isFalse();
    }

    @Test
    @DisplayName("isValidIp: accepts IPv4, rejects garbage")
    void isValidIp_validates() {
        assertThat(ClientIpResolver.isValidIp("10.0.0.1")).isTrue();
        assertThat(ClientIpResolver.isValidIp("::1")).isTrue();
        assertThat(ClientIpResolver.isValidIp("not-an-ip")).isFalse();
        assertThat(ClientIpResolver.isValidIp("")).isFalse();
        assertThat(ClientIpResolver.isValidIp(null)).isFalse();
    }
}
