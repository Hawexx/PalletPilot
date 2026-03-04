package com.palletpilot.wcs.application.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Summary of a single zone's occupancy status.
 */
@Data
@Builder
public class ZoneSummary {
    private String zoneClass;
    private long totalSlots;
    private long occupiedSlots;
    private double occupancyPercent;
}
