package com.atlassian.braid.document;

import com.atlassian.braid.mapper.MapperOperation;
import com.atlassian.braid.mapper.MapperOperations;
import graphql.language.Field;

import static java.util.Objects.requireNonNull;

public final class CopyFieldOperation implements FieldOperation {

    private final String name;
    private final String target;

    public CopyFieldOperation(String name, String target) {
        this.name = requireNonNull(name);
        this.target = requireNonNull(target);
    }

    @Override
    public boolean test(Field field) {
        return field.getName().equals(name);
    }

    @Override
    public OperationResult apply(MappingContext mappingContext, Field field) {
        final Field newField = new Field(target, field.getAlias(), field.getArguments(), field.getDirectives(), field.getSelectionSet());

        MappingContext.from(mappingContext, field);


        final String alias = newField.getAlias();

        final String sourceKey = alias == null ? name : alias;
        final String targetKey = alias == null ? target : alias;


        final MapperOperation operation = MapperOperations.copy(mappingContext.getSpringPath(targetKey), sourceKey);

        return FieldOperation.result(newField, operation);
    }
}
