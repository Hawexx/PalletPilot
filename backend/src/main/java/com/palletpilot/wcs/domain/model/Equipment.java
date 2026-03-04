package com.palletpilot.wcs.domain.model;

import com.palletpilot.wcs.domain.model.enums.EquipmentState;
import com.palletpilot.wcs.domain.model.enums.EquipmentType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "equipment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Equipment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EquipmentType type;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column
    private Integer port;

    @Column
    private String protocol;

    @Column(name = "api_key")
    private String apiKey;

    @Enumerated(EnumType.STRING)
    @Column
    private EquipmentState status;

    @Column(name = "config_json", columnDefinition = "TEXT")
    private String configJson; // JSON string for protocol-specific settings

    @Column(name = "last_heartbeat")
    private Instant lastHeartbeat;
}
