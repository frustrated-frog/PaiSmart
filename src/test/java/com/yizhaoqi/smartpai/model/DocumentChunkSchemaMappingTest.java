package com.yizhaoqi.smartpai.model;

import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DocumentChunkSchemaMappingTest {

    @Test
    void parentAndContextFieldsUseLongTextColumns() throws Exception {
        assertLongText(DocumentParentChunk.class, "textContent");
        assertLongText(DocumentVector.class, "contextualText");
    }

    private static void assertLongText(Class<?> entityType, String fieldName) throws Exception {
        Field field = entityType.getDeclaredField(fieldName);
        Column column = field.getAnnotation(Column.class);

        assertEquals("LONGTEXT", column.columnDefinition(),
                () -> entityType.getSimpleName() + "." + fieldName + " must support parsed document content");
    }
}
