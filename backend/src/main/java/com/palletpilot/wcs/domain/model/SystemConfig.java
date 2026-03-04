package com.palletpilot.wcs.domain.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "system_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemConfig {

    @Id
    @Column(name = "config_key", nullable = false)
    private String key;

    @Column(name = "config_value", length = 1024)
    private String value;

    @Column(length = 50)
    private String category; // e.g., "HARDWARE", "WMS", "SYSTEM"

    @Column
    private String description;
}
