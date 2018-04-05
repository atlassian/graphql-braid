package com.atlassian.braid.document;

import graphql.language.Field;

import static com.atlassian.braid.document.DocumentMapperPredicates.fieldNamed;
import static com.atlassian.braid.document.SelectionOperation.result;
import static com.atlassian.braid.document.Fields.getFieldAliasOrName;
import static com.atlassian.braid.mapper.MapperOperations.put;
import static java.util.Objects.requireNonNull;

final class PutFieldOperation extends AbstractTypeOperation<Field> {

    private final String value;

    PutFieldOperation(String name, String value) {
        super(Field.class, fieldNamed(name));
        this.value = requireNonNull(value);
    }

    @Override
    protected OperationResult applyToType(MappingContext mappingContext, Field field) {
        return result(put(getFieldAliasOrName(field), value));
    }
}
