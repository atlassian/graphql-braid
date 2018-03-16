package com.atlassian.braid.document;

import graphql.language.Field;

import static java.util.Objects.requireNonNull;

public class CopyObjectOperation implements FieldOperation {

    private final String name;
    private final String target;

    public CopyObjectOperation(String name, String target) {
        this.name = requireNonNull(name);
        this.target = requireNonNull(target);
    }

    @Override
    public boolean test(Field field) {
        return field.getName().equals(name);
    }

    @Override
    public OperationResult apply(MappingContext mappingContext, Field field) {
     return null;
    }
}
