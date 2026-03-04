package com.palletpilot.wcs.infrastructure.persistence.converter;

import com.palletpilot.wcs.domain.model.enums.EquipmentType;
import com.palletpilot.wcs.domain.model.workflow.WorkflowStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for WorkflowStepListConverter — JPA JSON ↔ List<WorkflowStep>.
 * Tests: round-trip, null handling, serialization structure, error wrapping.
 */
class WorkflowStepListConverterTest {

    private final WorkflowStepListConverter converter = new WorkflowStepListConverter();

    @Test
    @DisplayName("round-trip: List → JSON → List preserves data")
    void roundTrip_preservesData() {
        WorkflowStep step = WorkflowStep.builder()
                .stepId("S1")
                .equipmentType(EquipmentType.MOVU)
                .action("PICK")
                .parameters(Map.of("lane", 3))
                .nextStepId("S2")
                .build();

        String json = converter.convertToDatabaseColumn(List.of(step));
        List<WorkflowStep> result = converter.convertToEntityAttribute(json);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStepId()).isEqualTo("S1");
        assertThat(result.get(0).getAction()).isEqualTo("PICK");
        assertThat(result.get(0).getEquipmentType()).isEqualTo(EquipmentType.MOVU);
        assertThat(result.get(0).getNextStepId()).isEqualTo("S2");
    }

    @Test
    @DisplayName("convertToDatabaseColumn: null → null")
    void toDB_null_returnsNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    @DisplayName("convertToEntityAttribute: null → null")
    void toEntity_null_returnsNull() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    @DisplayName("convertToDatabaseColumn: empty list → valid JSON '[]'")
    void toDB_emptyList_validJson() {
        String json = converter.convertToDatabaseColumn(List.of());
        assertThat(json).isEqualTo("[]");
    }

    @Test
    @DisplayName("convertToEntityAttribute: empty JSON array → empty list")
    void toEntity_emptyArray_emptyList() {
        List<WorkflowStep> result = converter.convertToEntityAttribute("[]");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("convertToEntityAttribute: invalid JSON → throws IllegalArgumentException")
    void toEntity_invalidJson_throws() {
        assertThatThrownBy(() -> converter.convertToEntityAttribute("{NOT_VALID"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Error converting JSON");
    }

    @Test
    @DisplayName("round-trip: multiple steps with RetryPolicy")
    void roundTrip_multipleStepsWithRetry() {
        WorkflowStep.RetryPolicy policy = new WorkflowStep.RetryPolicy(3, 1000L);
        WorkflowStep s1 = WorkflowStep.builder().stepId("A").action("MOVE").retryPolicy(policy).build();
        WorkflowStep s2 = WorkflowStep.builder().stepId("B").action("PLACE").build();

        String json = converter.convertToDatabaseColumn(List.of(s1, s2));
        List<WorkflowStep> result = converter.convertToEntityAttribute(json);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getRetryPolicy().getMaxRetries()).isEqualTo(3);
        assertThat(result.get(0).getRetryPolicy().getDelayMs()).isEqualTo(1000L);
    }
}
