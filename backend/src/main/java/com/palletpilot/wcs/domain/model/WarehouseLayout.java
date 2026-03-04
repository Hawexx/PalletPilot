package com.palletpilot.wcs.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "warehouse_layout")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseLayout {

    @Id
    @Column(name = "id")
    private String id; // Singleton key "DEFAULT"

    @Column(name = "layout_json", columnDefinition = "TEXT")
    private String layoutJson;
}
