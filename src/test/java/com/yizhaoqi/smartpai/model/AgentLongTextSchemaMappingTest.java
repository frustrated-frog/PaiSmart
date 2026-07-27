package com.yizhaoqi.smartpai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Lob;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentLongTextSchemaMappingTest {

    private static final List<Class<?>> AGENT_ENTITIES = List.of(
            AgentRun.class,
            AgentCheckpoint.class,
            AgentMemory.class,
            AgentPendingTask.class,
            AgentToolCall.class,
            AgentStep.class
    );

    private static final List<String> REQUIRED_LONG_TEXT_FIELDS = List.of(
            "AgentRun.question",
            "AgentRun.answer",
            "AgentRun.errorMessage",
            "AgentCheckpoint.stateJson",
            "AgentMemory.content",
            "AgentMemory.sourceQuery",
            "AgentPendingTask.originalQuery",
            "AgentPendingTask.knownSlotsJson",
            "AgentPendingTask.missingSlotsJson",
            "AgentPendingTask.question",
            "AgentPendingTask.optionsJson",
            "AgentToolCall.argumentsJson",
            "AgentToolCall.resultContent",
            "AgentToolCall.resultDataJson",
            "AgentToolCall.errorMessage",
            "AgentStep.detail",
            "AgentStep.metadataJson"
    );

    @Test
    void allUnboundedAgentFieldsUseLongTextColumns() {
        List<FieldMapping> mappings = AGENT_ENTITIES.stream()
                .flatMap(entityType -> Arrays.stream(entityType.getDeclaredFields())
                        .filter(field -> field.getType() == String.class)
                        .filter(field -> field.isAnnotationPresent(Lob.class))
                        .map(field -> new FieldMapping(entityType, field.getName())))
                .toList();

        List<String> discoveredFields = mappings.stream()
                .map(mapping -> mapping.entityType().getSimpleName() + "." + mapping.fieldName())
                .toList();

        assertTrue(discoveredFields.containsAll(REQUIRED_LONG_TEXT_FIELDS),
                () -> "Missing expected Agent long-text mappings: " + discoveredFields);

        assertAll(mappings.stream().map(mapping -> () -> assertLongText(mapping)));
    }

    private static void assertLongText(FieldMapping mapping) throws NoSuchFieldException {
        Field field = mapping.entityType().getDeclaredField(mapping.fieldName());
        Column column = field.getAnnotation(Column.class);

        assertEquals("LONGTEXT", column.columnDefinition(),
                () -> mapping.entityType().getSimpleName() + "." + mapping.fieldName()
                        + " must support unbounded Agent runtime content");
    }

    private record FieldMapping(Class<?> entityType, String fieldName) {
    }
}
