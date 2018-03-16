package com.atlassian.braid.document;

import com.atlassian.braid.mapper.MapperOperations;
import graphql.language.Field;

import static java.util.Objects.requireNonNull;

public final class PutFieldOperation implements FieldOperation {

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
    public OperationResult apply(MappingContext mappingContext, Field field) {
        return FieldOperation.result(MapperOperations.put(name, value));
    }
}
