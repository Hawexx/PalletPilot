package com.palletpilot.wcs.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Runtime ERP connection and security configuration.
 * Supports per-warehouse config for multi-warehouse deployments.
 */
@Entity
@Table(name = "erp_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErpConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "warehouse_code", length = 50)
    private String warehouseCode;

    @Column(name = "erp_type", nullable = false, length = 20)
    private String erpType; // SAP, ORACLE, CUSTOM

    @Column(name = "integration_mode", nullable = false, length = 20)
    private String integrationMode; // WEBHOOK, POLLING, FILE

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;

    // ── Polling Mode ──
    @Column(name = "base_url")
    private String baseUrl;

    @Column(name = "api_key_encrypted", columnDefinition = "TEXT")
    private String apiKeyEncrypted;

    // ── Webhook Security ──
    @Column(name = "webhook_secret_encrypted", columnDefinition = "TEXT")
    private String webhookSecretEncrypted;

    @Column(name = "ip_allowlist_cidrs")
    private String ipAllowlistCidrs; // comma-separated

    @Builder.Default
    @Column(name = "rate_limit_per_min")
    private Integer rateLimitPerMin = 100;

    @Builder.Default
    @Column(name = "timestamp_max_age_seconds")
    private Integer timestampMaxAgeSeconds = 300;

    // ── Outbound Callback ──
    @Column(name = "callback_url")
    private String callbackUrl;

    @Column(name = "callback_api_key_encrypted", columnDefinition = "TEXT")
    private String callbackApiKeyEncrypted;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
