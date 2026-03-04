package com.palletpilot.wcs.infrastructure.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Arrays;

/**
 * Validates that critical secrets are configured at startup.
 *
 * - In 'dev' profile: logs warnings for missing secrets but allows startup.
 * - In 'prod' profile (or no profile): fails fast if DB_PASSWORD is missing.
 * - Optional secrets (WMS, Movu, ERP) generate warnings but never block
 * startup.
 */
@Configuration
@Slf4j
public class SecretValidationConfig {

    private final Environment environment;

    @Value("${spring.datasource.password:}")
    private String dbPassword;

    @Value("${hardware.movu.api-key:}")
    private String movuApiKey;

    @Value("${wms.password:}")
    private String wmsPassword;

    @Value("${erp.webhook.secret:}")
    private String erpWebhookSecret;

    @Value("${app.api-key.key:}")
    private String apiKey;

    @Value("${app.api-key.enabled:false}")
    private boolean apiKeyEnabled;

    public SecretValidationConfig(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void validateSecrets() {
        boolean isDevProfile = Arrays.asList(environment.getActiveProfiles()).contains("dev");

        log.info("══════════════════════════════════════════════");
        log.info("  PalletPilot — Secret Validation");
        log.info("  Active profiles: {}", Arrays.toString(environment.getActiveProfiles()));
        log.info("══════════════════════════════════════════════");

        // ── Critical: DB Password ──
        if (dbPassword == null || dbPassword.isBlank()) {
            if (isDevProfile) {
                log.warn("[SECRET] DB_PASSWORD is empty — dev profile will use application-dev.properties fallback");
            } else {
                log.error("[SECRET] ╔══════════════════════════════════════════════════════╗");
                log.error("[SECRET] ║  FATAL: DB_PASSWORD is not configured!              ║");
                log.error("[SECRET] ║  Set DB_PASSWORD environment variable or use         ║");
                log.error("[SECRET] ║  --spring.profiles.active=dev for local development  ║");
                log.error("[SECRET] ╚══════════════════════════════════════════════════════╝");
                throw new IllegalStateException(
                        "DB_PASSWORD is required. Set it via environment variable or activate 'dev' profile.");
            }
        } else {
            log.info("[SECRET] ✓ DB_PASSWORD is configured");
        }

        // ── Optional: Movu API Key ──
        if (movuApiKey == null || movuApiKey.isBlank()) {
            log.warn("[SECRET] ⚠ MOVU_API_KEY is not configured — Movu aSTC calls will fail (OK if simulation mode)");
        } else {
            log.info("[SECRET] ✓ MOVU_API_KEY is configured");
        }

        // ── Optional: WMS Password ──
        if (wmsPassword == null || wmsPassword.isBlank()) {
            log.info("[SECRET] ○ WMS_PASSWORD is not configured (WMS integration disabled)");
        } else {
            log.info("[SECRET] ✓ WMS_PASSWORD is configured");
        }

        // ── Optional: ERP Webhook Secret ──
        if (erpWebhookSecret == null || erpWebhookSecret.isBlank()) {
            log.warn("[SECRET] ⚠ ERP_WEBHOOK_SECRET is not configured — HMAC verification disabled");
        } else {
            log.info("[SECRET] ✓ ERP_WEBHOOK_SECRET is configured");
        }

        // ── Conditional: API Key ──
        if (apiKeyEnabled && (apiKey == null || apiKey.isBlank())) {
            log.error("[SECRET] ╔══════════════════════════════════════════════════════╗");
            log.error("[SECRET] ║  WARNING: API key auth is ENABLED but API_KEY is    ║");
            log.error("[SECRET] ║  empty — all /api/** requests will be rejected!      ║");
            log.error("[SECRET] ╚══════════════════════════════════════════════════════╝");
        } else if (apiKeyEnabled) {
            log.info("[SECRET] ✓ API_KEY is configured (authentication enabled)");
        }

        log.info("══════════════════════════════════════════════");
    }
}
