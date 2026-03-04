package com.palletpilot.wcs.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "charging_stations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChargingStation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    // Location coordinates
    @Column(nullable = false)
    private Integer x;

    @Column(nullable = false)
    private Integer y;

    @Column(name = "is_occupied", nullable = false)
    private boolean isOccupied;

    @Column(name = "occupied_by_robot_id")
    private UUID occupiedByRobotId;
}
