package com.atlassian.braid.document;

import graphql.language.Selection;

import java.util.function.Predicate;

import static com.atlassian.braid.document.SelectionMapper.getSelectionMapper;
import static com.atlassian.braid.java.util.BraidObjects.cast;
import static java.util.Objects.requireNonNull;

abstract class AbstractTypeOperation<S extends Selection> implements SelectionOperation {

    private final Class<S> type;
    private final Predicate<S> predicate;

    AbstractTypeOperation(Class<S> type) {
        this(type, __ -> true);
    }

    AbstractTypeOperation(Class<S> type, Predicate<S> predicate) {
        this.type = requireNonNull(type);
        this.predicate = requireNonNull(predicate);
    }

    @Override
    public final boolean test(Selection selection) {
        return type.isAssignableFrom(selection.getClass()) && predicate.test(cast(selection));
    }

    @Override
    public final OperationResult apply(MappingContext mappingContext, Selection selection) {
        return applyToType(mappingContext, type.cast(selection));
    }

    protected OperationResult applyToType(MappingContext mappingContext, S selection) {
        return getSelectionMapper(selection).map(mappingContext);
    }
}
