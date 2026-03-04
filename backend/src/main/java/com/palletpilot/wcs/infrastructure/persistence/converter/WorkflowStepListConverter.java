package com.palletpilot.wcs.infrastructure.persistence.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.palletpilot.wcs.domain.model.workflow.WorkflowStep;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.io.IOException;
import java.util.List;

@Converter(autoApply = true)
public class WorkflowStepListConverter implements AttributeConverter<List<WorkflowStep>, String> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<WorkflowStep> attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Error converting WorkflowSteps to JSON", e);
        }
    }

    @Override
    public List<WorkflowStep> convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        try {
            return objectMapper.readValue(dbData, new TypeReference<List<WorkflowStep>>() {
            });
        } catch (IOException e) {
            throw new IllegalArgumentException("Error converting JSON to WorkflowSteps", e);
        }
    }
}
