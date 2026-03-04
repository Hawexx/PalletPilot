package com.palletpilot.wcs.infrastructure.web.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardStats {
    private long activeOrders;
    private long activeMissions;
    private long totalEquipment;
    private long onlineEquipment;
    private double equipmentHealthPercentage;
    private String systemStatus; // Operational, Degraded, Critical (Derived from health)
    private String globalState; // RUNNING, STOPPED, EMERGENCY_STOP (Global Switch)
}
