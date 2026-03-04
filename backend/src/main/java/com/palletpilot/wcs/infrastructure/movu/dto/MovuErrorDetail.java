package com.palletpilot.wcs.infrastructure.movu.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Error detail from MissionError webhook notifications.
 * Example: {"id": 0, "message": "Lane is full, no loads can be added",
 * "severity": "Warning"}
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MovuErrorDetail {
    private Integer id;
    private String message;
    private String severity;
}
