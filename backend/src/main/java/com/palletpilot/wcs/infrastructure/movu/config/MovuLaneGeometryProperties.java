package com.palletpilot.wcs.infrastructure.movu.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Lane geometry configuration for Atlas 2D distance calculations.
 * Used by MovuLateDecisionService to compute safe pallet positions.
 */
@Component
@ConfigurationProperties(prefix = "hardware.movu.lane")
@Getter
@Setter
public class MovuLaneGeometryProperties {

    /** Total lane length in mm */
    private int laneLengthMm = 12000;

    /** Maximum load (pallet) length in mm (euro pallet = ~1200mm) */
    private int maxLoadLengthMm = 1200;

    /**
     * Default gap between pallets in mm (industrial shuttle + euro pallet = 150mm)
     */
    private int defaultGapMm = 150;

    /** Distance offset from front edge of lane in mm */
    private int offsetFrontMm = 200;

    /** Distance offset from back edge of lane in mm */
    private int offsetBackMm = 200;

    /** Minimum gap for repositioning in mm */
    private int minGapRepositioningMm = 50;

    /** Minimum gap for industrial shuttle + euro pallet in mm */
    private int minGapIndustrialEuroMm = 150;

    /** Minimum gap for industrial shuttle + industrial pallet in mm */
    private int minGapIndustrialIndustrialMm = 50;
}
