package com.atlassian.braid.document;

import com.atlassian.braid.document.SelectionOperation.OperationResult;
import graphql.language.Field;
import graphql.language.Selection;

import static com.atlassian.braid.document.SelectionOperation.result;

abstract class SelectionMapper<S extends Selection> {

    static SelectionMapper<?> getSelectionMapper(Selection selection) {
        if (selection instanceof Field) {
            return new FieldMapper((Field) selection);
        } else {
            throw new IllegalStateException();
        }
    }

    abstract OperationResult map(MappingContext mappingContext);

    private static class FieldMapper extends SelectionMapper<Field> {
        private final Field field;

        private FieldMapper(Field field) {
            this.field = field;
        }

        OperationResult map(MappingContext mappingContext) {
            final MappingContext fieldMappingContext = mappingContext.forField(field);

            return fieldMappingContext.getTypeMapper()
                    .map(typeMapper -> typeMapper.apply(fieldMappingContext, field.getSelectionSet()))
                    .map(mappingResult -> mappingResult.toOperationResult(field, fieldMappingContext))
                    .orElseGet(() -> result(field));
        }
    }
}
