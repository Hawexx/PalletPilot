package com.palletpilot.wcs.application.service;

import com.palletpilot.wcs.domain.model.enums.EquipmentType;
import com.palletpilot.wcs.domain.model.workflow.WorkflowDefinition;
import com.palletpilot.wcs.domain.model.workflow.WorkflowStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for WorkflowValidationService — safety + logic validation.
 * Tests duplicate step IDs, missing fields, layer safety limits,
 * and graph integrity (dangling nextStepId references).
 */
class WorkflowValidationServiceTest {

    private final WorkflowValidationService validator = new WorkflowValidationService();

    @Test
    @DisplayName("valid workflow with 2 linked steps → passes")
    void validWorkflow_passes() {
        WorkflowStep s1 = WorkflowStep.builder()
                .stepId("S1").equipmentType(EquipmentType.CONVEYOR).action("MOVE").nextStepId("S2").build();
        WorkflowStep s2 = WorkflowStep.builder()
                .stepId("S2").equipmentType(EquipmentType.MOVU).action("PICK").build();
        WorkflowDefinition def = WorkflowDefinition.builder().steps(List.of(s1, s2)).build();

        assertThatCode(() -> validator.validateWorkflow(def)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("null steps → throws")
    void nullSteps_throws() {
        WorkflowDefinition def = WorkflowDefinition.builder().build();
        assertThatThrownBy(() -> validator.validateWorkflow(def))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one step");
    }

    @Test
    @DisplayName("empty steps → throws")
    void emptySteps_throws() {
        WorkflowDefinition def = WorkflowDefinition.builder().steps(List.of()).build();
        assertThatThrownBy(() -> validator.validateWorkflow(def))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one step");
    }

    @Test
    @DisplayName("duplicate step IDs → throws")
    void duplicateStepIds_throws() {
        WorkflowStep s1 = WorkflowStep.builder()
                .stepId("DUP").equipmentType(EquipmentType.CONVEYOR).action("MOVE").build();
        WorkflowStep s2 = WorkflowStep.builder()
                .stepId("DUP").equipmentType(EquipmentType.MOVU).action("PICK").build();
        WorkflowDefinition def = WorkflowDefinition.builder().steps(List.of(s1, s2)).build();

        assertThatThrownBy(() -> validator.validateWorkflow(def))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate Step ID");
    }

    @Test
    @DisplayName("missing equipmentType → throws")
    void missingEquipmentType_throws() {
        WorkflowStep s = WorkflowStep.builder().stepId("S1").action("MOVE").build();
        WorkflowDefinition def = WorkflowDefinition.builder().steps(List.of(s)).build();

        assertThatThrownBy(() -> validator.validateWorkflow(def))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("EquipmentType or Action");
    }

    @Test
    @DisplayName("missing action → throws")
    void missingAction_throws() {
        WorkflowStep s = WorkflowStep.builder().stepId("S1").equipmentType(EquipmentType.CONVEYOR).build();
        WorkflowDefinition def = WorkflowDefinition.builder().steps(List.of(s)).build();

        assertThatThrownBy(() -> validator.validateWorkflow(def))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("EquipmentType or Action");
    }

    @Test
    @DisplayName("SAFETY: layerIndex > 4 → throws")
    void layerIndexExceedsMax_throws() {
        WorkflowStep s = WorkflowStep.builder()
                .stepId("S1").equipmentType(EquipmentType.KUKA).action("PLACE")
                .parameters(Map.of("layerIndex", 5)).build();
        WorkflowDefinition def = WorkflowDefinition.builder().steps(List.of(s)).build();

        assertThatThrownBy(() -> validator.validateWorkflow(def))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SAFETY VIOLATION");
    }

    @Test
    @DisplayName("SAFETY: layerIndex = 4 → passes")
    void layerIndexAtMax_passes() {
        WorkflowStep s = WorkflowStep.builder()
                .stepId("S1").equipmentType(EquipmentType.KUKA).action("PLACE")
                .parameters(Map.of("layerIndex", 4)).build();
        WorkflowDefinition def = WorkflowDefinition.builder().steps(List.of(s)).build();

        assertThatCode(() -> validator.validateWorkflow(def)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("dangling nextStepId reference → throws")
    void danglingNextStepId_throws() {
        WorkflowStep s = WorkflowStep.builder()
                .stepId("S1").equipmentType(EquipmentType.CONVEYOR).action("MOVE")
                .nextStepId("GHOST").build();
        WorkflowDefinition def = WorkflowDefinition.builder().steps(List.of(s)).build();

        assertThatThrownBy(() -> validator.validateWorkflow(def))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown next step");
    }
}
