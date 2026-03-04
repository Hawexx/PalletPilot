package com.palletpilot.wcs.domain.model;

import com.palletpilot.wcs.domain.model.enums.RobotState;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "robots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Robot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    // Real-time position
    @Column(nullable = false)
    private Integer x; // Aisle X

    @Column(nullable = false)
    private Integer y; // Cross-aisle Y

    @Column(nullable = false)
    private Integer z; // Lift Z

    @Column(name = "battery_level", nullable = false)
    private Integer batteryLevel; // 0-100

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RobotState state;

    @Column(name = "current_task_id")
    private UUID currentTaskId; // Link to a Task (if we have a Task entity later)

    @Column(name = "assigned_charging_station_id")
    private UUID assignedChargingStationId;
}
