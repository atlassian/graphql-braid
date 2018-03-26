package com.atlassian.braid.document;

import graphql.language.Field;
import graphql.language.SelectionSet;

import java.util.Optional;

import static com.atlassian.braid.document.FieldOperation.result;
import static com.atlassian.braid.document.Fields.cloneFieldWithNewName;
import static com.atlassian.braid.document.TypedDocumentMapper.mapNode;
import static com.atlassian.braid.mapper.MapperOperations.copy;
import static java.util.Objects.requireNonNull;

final class CopyFieldOperation implements FieldOperation {

    private final String name;
    private final String target;

    CopyFieldOperation(String name, String target) {
        this.name = requireNonNull(name);
        this.target = requireNonNull(target);
    }

    @Override
    public boolean test(Field field) {
        return field.getName().equals(name);
    }

    @Override
    public FieldOperationResult apply(MappingContext mappingContext, Field field) {
        return getSelectionSet(field)
                .map(__ -> mapNode(mappingContext.to(field))) // graph node (object field)
                .orElseGet(() -> mapLeaf(mappingContext, field)); // graph leaf ('scalar' field)
    }


    private FieldOperationResult mapLeaf(MappingContext mappingContext, Field field) {
        final String alias = field.getAlias();
        final String sourceKey = alias == null ? name : alias;
        final String targetKey = alias == null ? target : alias;

        return result(
                cloneFieldWithNewName(field, target),
                copy(mappingContext.getSpringPath(targetKey), sourceKey));
    }

    private static Optional<SelectionSet> getSelectionSet(Field field) {
        return Optional.ofNullable(field.getSelectionSet());
    }
}
