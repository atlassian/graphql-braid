package com.atlassian.braid.document;

import graphql.language.Field;

import static com.atlassian.braid.document.FieldOperation.result;
import static com.atlassian.braid.mapper.MapperOperations.put;
import static java.util.Objects.requireNonNull;

final class PutFieldOperation implements FieldOperation {

    private final String name;
    private final String value;

    PutFieldOperation(String name, String value) {
        this.name = requireNonNull(name);
        this.value = requireNonNull(value);
    }

    @Override
    public boolean test(Field field) {
        return field.getName().equals(name);
    }

    @Override
    public FieldOperationResult apply(MappingContext mappingContext, Field field) {
        return result(put(name, value));
    }
}
