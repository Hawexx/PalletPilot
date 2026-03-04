package com.palletpilot.wcs.domain.model.workflow;

import com.palletpilot.wcs.domain.model.enums.EquipmentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowStep {
    private String stepId;
    private EquipmentType equipmentType;
    private String action; // e.g., "PICK", "PLACE", "MOVE"
    private Map<String, Object> parameters; // e.g., {"source": "A-01", "speed": 100}
    private String nextStepId; // Simple linked list style or graph edge
    private RetryPolicy retryPolicy;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RetryPolicy {
        private int maxRetries;
        private long delayMs;
    }
}
