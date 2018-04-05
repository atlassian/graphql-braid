package com.atlassian.braid.document;

import com.atlassian.braid.mapper.MapperOperation;
import graphql.language.Selection;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import static com.atlassian.braid.mapper.MapperOperations.noop;

interface SelectionOperation extends Predicate<Selection>, BiFunction<MappingContext, Selection, SelectionOperation.OperationResult> {

    @Override
    default boolean test(Selection selection) {
        return false;
    }

    @Override
    default OperationResult apply(MappingContext mappingContext, Selection selection) {
        return emptyResult();
    }

    static OperationResult emptyResult() {
        return result(null, null);
    }

    static OperationResult result(Selection selection) {
        return result(selection, null);
    }

    static OperationResult result(MapperOperation mapper) {
        return result(null, mapper);
    }

    static OperationResult result(Selection selection, MapperOperation mapper) {
        return new OperationResultImpl(selection, mapper);
    }

    interface OperationResult {
        default Optional<Selection> getSelection() {
            return Optional.empty();
        }

        default MapperOperation getMapper() {
            return noop();
        }
    }

    class OperationResultImpl implements OperationResult {
        private final Selection selection;
        private final MapperOperation mapper;

        OperationResultImpl(Selection selection, MapperOperation mapper) {
            this.selection = selection;
            this.mapper = mapper == null ? noop() : mapper;
        }

        @Override
        public Optional<Selection> getSelection() {
            return Optional.ofNullable(selection);
        }

        @Override
        public MapperOperation getMapper() {
            return mapper;
        }
    }
}
