package com.palletpilot.wcs.infrastructure.movu.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Typed payload from Movu mission webhook notifications.
 * Handles both camelCase (v1.2+) and PascalCase (v1.0) field names.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MovuMissionPayload {

    @JsonProperty("missionId")
    @JsonAlias({ "MissionId" })
    private String missionId;

    @JsonProperty("vehicleId")
    @JsonAlias({ "VehicleId" })
    private String vehicleId;

    @JsonProperty("loadId")
    @JsonAlias({ "LoadId" })
    private String loadId;

    @JsonProperty("priority")
    @JsonAlias({ "Priority" })
    private Integer priority;

    @JsonProperty("source")
    @JsonAlias({ "Source" })
    private MovuLocation source;

    @JsonProperty("destination")
    @JsonAlias({ "Destination" })
    private MovuLocation destination;

    @JsonProperty("errors")
    @JsonAlias({ "Errors" })
    private List<MovuErrorDetail> errors;
}
