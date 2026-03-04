package com.palletpilot.wcs.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Centralized client IP resolver that honours X-Forwarded-For
 * ONLY when the direct remote address is a trusted reverse proxy.
 *
 * Rules:
 * 1. Default: request.getRemoteAddr()
 * 2. If security.use-forwarded-headers=true AND remoteAddr matches a
 * trusted proxy CIDR → take the left-most X-Forwarded-For value.
 * 3. Invalid XFF or non-IP → fall back to remoteAddr.
 *
 * This prevents unbounded key-space expansion from spoofed XFF headers.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ClientIpResolver {

    private final TrustedProxyProperties proxyProps;

    /**
     * Resolve the real client IP from the request.
     */
    public String resolveClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();

        if (!proxyProps.isUseForwardedHeaders()) {
            return remoteAddr;
        }

        if (!isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }

        // Remote is a trusted proxy → extract client IP from XFF
        String xff = request.getHeader("X-Forwarded-For");
        if (xff == null || xff.isBlank()) {
            return remoteAddr;
        }

        // Take the left-most IP (original client)
        String clientIp = xff.split(",")[0].trim();

        if (isValidIp(clientIp)) {
            return clientIp;
        }

        log.warn("Invalid IP in X-Forwarded-For header: '{}', falling back to remoteAddr", clientIp);
        return remoteAddr;
    }

    /**
     * Check if the given IP matches any trusted proxy CIDR.
     */
    boolean isTrustedProxy(String ip) {
        if (proxyProps.getTrustedProxies().isEmpty()) {
            return false;
        }

        try {
            InetAddress addr = InetAddress.getByName(ip);
            for (String cidr : proxyProps.getTrustedProxies()) {
                if (matchesCidr(addr, cidr)) {
                    return true;
                }
            }
        } catch (UnknownHostException e) {
            log.warn("Cannot resolve IP '{}' for trusted proxy check", ip);
        }
        return false;
    }

    /**
     * CIDR match for IPv4 (and IPv6 if address lengths match).
     */
    static boolean matchesCidr(InetAddress clientAddr, String cidr) {
        try {
            String[] parts = cidr.split("/");
            InetAddress networkAddr = InetAddress.getByName(parts[0]);
            int prefixLen = parts.length > 1 ? Integer.parseInt(parts[1]) : 32;

            byte[] clientBytes = clientAddr.getAddress();
            byte[] networkBytes = networkAddr.getAddress();
            if (clientBytes.length != networkBytes.length) {
                return false;
            }

            int fullBytes = prefixLen / 8;
            int remainingBits = prefixLen % 8;

            for (int i = 0; i < fullBytes; i++) {
                if (clientBytes[i] != networkBytes[i]) {
                    return false;
                }
            }
            if (remainingBits > 0 && fullBytes < clientBytes.length) {
                int mask = 0xFF << (8 - remainingBits);
                if ((clientBytes[fullBytes] & mask) != (networkBytes[fullBytes] & mask)) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Validate that the string is a plausible IPv4 or IPv6 address.
     */
    static boolean isValidIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        try {
            InetAddress.getByName(ip);
            // Reject hostnames: InetAddress.getByName resolves DNS too,
            // so also check for numeric format
            return ip.contains(".") || ip.contains(":");
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
