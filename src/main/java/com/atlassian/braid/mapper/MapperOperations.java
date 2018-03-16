package com.atlassian.braid.mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.function.Function.identity;

/**
 * Helper class to deal with common operations handling
 */
public final class MapperOperations {

    private MapperOperations() {
    }

    public static MapperOperation noop() {
        return new NoopOperation();
    }

    public static MapperOperation composed(MapperOperation... operations) {
        return composed(asList(operations));
    }

    public static MapperOperation map(String key, Mapper mapper) {
        return new MapOperation(key, __ -> true, mapper);
    }

    public static <T> MapperOperation copy(String sourceKey, String targetKey) {
        return new CopyOperation<T, T>(sourceKey, targetKey, () -> null, identity());
    }

    public static MapperOperation put(String key, String value) {
        return new PutOperation<>(key, value);
    }

    static MapperOperation composed(List<MapperOperation> operations) {
        return new ComposedOperation(operations);
    }

    private static class NoopOperation implements MapperOperation {
        @Override
        public void accept(Map<String, Object> input, Map<String, Object> output) {
        }
    }

    private static class ComposedOperation implements MapperOperation {
        private final List<MapperOperation> operations;

        private ComposedOperation(List<MapperOperation> operations) {
            this.operations = new ArrayList<>(operations);
        }

        @Override
        public void accept(Map<String, Object> input, Map<String, Object> output) {
            operations.forEach(op -> op.accept(input, output));
        }
    }
}
