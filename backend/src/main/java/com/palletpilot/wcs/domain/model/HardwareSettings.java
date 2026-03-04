package com.palletpilot.wcs.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "hardware_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HardwareSettings {

    @Id
    @Column(name = "config_key", nullable = false)
    private String configKey; // e.g. "movu", "kuka"

    @Column(name = "base_url")
    private String baseUrl;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "port")
    private Integer port;

    @Column(name = "api_key")
    private String apiKey;

    @Column(name = "timeout_ms")
    private String timeoutMs;
}
