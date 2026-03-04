package com.palletpilot.wcs.infrastructure.movu.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a Movu warehouse location.
 * Atlas 2D uses distance (mm); eScala uses index.
 * Handles both camelCase (v1.2) and PascalCase (v1.0) JSON keys.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MovuLocation {

    @JsonProperty("level")
    private Integer level;

    @JsonProperty("lane")
    private Integer lane;

    @JsonProperty("section")
    private Integer section;

    @JsonProperty("index")
    private Integer index;

    @JsonProperty("distance")
    private Integer distance;

    /** Source locations use "exit" (Front/Back). */
    @JsonProperty("exit")
    private String exit;

    /** Destination locations use "approach" (Front/Back). */
    @JsonProperty("approach")
    private String approach;
}
