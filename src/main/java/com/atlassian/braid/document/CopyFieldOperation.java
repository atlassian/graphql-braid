package com.atlassian.braid.document;

import graphql.language.Field;
import graphql.language.SelectionSet;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.atlassian.braid.document.DocumentMapperPredicates.all;
import static com.atlassian.braid.document.DocumentMapperPredicates.fieldNamed;
import static com.atlassian.braid.document.SelectionMapper.getSelectionMapper;
import static com.atlassian.braid.document.SelectionOperation.result;
import static com.atlassian.braid.document.Fields.cloneFieldWithNewName;
import static com.atlassian.braid.document.Fields.getFieldAliasOrName;
import static com.atlassian.braid.mapper.MapperOperations.copy;
import static java.util.Objects.requireNonNull;

final class CopyFieldOperation extends AbstractTypeOperation<Field> {

    private static final String ANY_NAME = "*";

    private final Function<Field, String> target;

    CopyFieldOperation() {
        this(ANY_NAME, ANY_NAME);
    }

    CopyFieldOperation(String key, String target) {
        this(copyPredicate(key), copyTarget(target));
    }

    private CopyFieldOperation(Predicate<Field> predicate, Function<Field, String> target) {
        super(Field.class, predicate);
        this.target = requireNonNull(target);
    }

    @Override
    protected OperationResult applyToType(MappingContext mappingContext, Field field) {
        return getSelectionSet(field)
                .map(__ -> getSelectionMapper(field).map(mappingContext)) // graph node (object field)
                .orElseGet(() -> mapLeaf(mappingContext, field)); // graph leaf ('scalar' field)
    }

    private OperationResult mapLeaf(MappingContext mappingContext, Field field) {
        final String targetKey = target.apply(field);
        return result(
                cloneFieldWithNewName(field, targetKey),
                copy(mappingContext.getSpringPath(targetKey), getFieldAliasOrName(field)));
    }

    private static Optional<SelectionSet> getSelectionSet(Field field) {
        return Optional.ofNullable(field.getSelectionSet());
    }

    private static Predicate<Field> copyPredicate(String key) {
        return Objects.equals("*", key) ? all() : fieldNamed(key);
    }

    private static Function<Field, String> copyTarget(String target) {
        return Objects.equals(ANY_NAME, target) ? Fields::getFieldAliasOrName : __ -> target;
    }
}
