package com.atlassian.braid.document;

import com.atlassian.braid.mapper.MapperOperation;
import graphql.language.Field;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import static com.atlassian.braid.mapper.MapperOperations.noop;

public interface FieldOperation extends Predicate<Field>, BiFunction<MappingContext, Field, FieldOperation.OperationResult> {

    @Override
    default boolean test(Field field) {
        return false;
    }

    @Override
    default OperationResult apply(MappingContext mappingContext, Field field) {
        return new OperationResult() {
        };
    }

    static OperationResult result(Field field) {
        return result(field, null);
    }

    static OperationResult result(MapperOperation mapper) {
        return result(null, mapper);
    }

    static OperationResult result(Field field, MapperOperation mapper) {
        return new OperationResultImpl(field, mapper);
    }

    interface OperationResult {
        default Optional<Field> getField() {
            return Optional.empty();
        }

        default MapperOperation getMapper() {
            return noop();
        }
    }

    class OperationResultImpl implements OperationResult {
        private final Field field;
        private final MapperOperation mapper;

        public OperationResultImpl(Field field, MapperOperation mapper) {
            this.field = field;
            this.mapper = mapper == null ? noop() : mapper;
        }

        @Override
        public Optional<Field> getField() {
            return Optional.ofNullable(field);
        }

        @Override
        public MapperOperation getMapper() {
            return mapper;
        }
    }
}
