package com.atlassian.braid.document;

import com.atlassian.braid.mapper.MapperOperation;
import graphql.language.Field;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import static com.atlassian.braid.mapper.MapperOperations.noop;

interface FieldOperation extends Predicate<Field>, BiFunction<MappingContext, Field, FieldOperation.FieldOperationResult> {

    @Override
    default boolean test(Field field) {
        return false;
    }

    @Override
    default FieldOperationResult apply(MappingContext mappingContext, Field field) {
        return new FieldOperationResult() {
        };
    }

    static FieldOperationResult result(Field field) {
        return result(field, null);
    }

    static FieldOperationResult result(MapperOperation mapper) {
        return result(null, mapper);
    }

    static FieldOperationResult result(Field field, MapperOperation mapper) {
        return new FieldOperationResultImpl(field, mapper);
    }

    interface FieldOperationResult {
        default Optional<Field> getField() {
            return Optional.empty();
        }

        default MapperOperation getMapper() {
            return noop();
        }
    }

    class FieldOperationResultImpl implements FieldOperationResult {
        private final Field field;
        private final MapperOperation mapper;

        FieldOperationResultImpl(Field field, MapperOperation mapper) {
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
