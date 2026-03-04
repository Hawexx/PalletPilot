package com.palletpilot.wcs.domain.model.layout;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LayoutNode {
    private String id;
    private String type; // CONVEYOR, MOVU, KUKA, etc.
    private double x;
    private double y;
    private String label;
    private double width;
    private double height;
    private Map<String, Object> config;
}
