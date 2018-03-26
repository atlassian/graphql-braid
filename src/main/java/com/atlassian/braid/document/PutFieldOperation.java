package com.atlassian.braid.document;

import graphql.language.Field;

import static com.atlassian.braid.document.DocumentMapperPredicates.fieldNamed;
import static com.atlassian.braid.document.FieldOperation.result;
import static com.atlassian.braid.document.Fields.getFieldAliasOrName;
import static com.atlassian.braid.mapper.MapperOperations.put;
import static java.util.Objects.requireNonNull;

final class PutFieldOperation extends AbstractFieldOperation {

    private final String value;

    PutFieldOperation(String name, String value) {
        super(fieldNamed(name));
        this.value = requireNonNull(value);
    }

    @Override
    public FieldOperationResult apply(MappingContext mappingContext, Field field) {
        return result(put(getFieldAliasOrName(field), value));
    }
}
