package com.atlassian.braid.mapper2;

import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

final class CopyOperation<T, R> implements MapperOperation {
    private final BiFunction<Map<String, Object>, String, Optional<Object>> getFromMap;

    private final String sourceKey;
    private final String targetKey;
    private final Supplier<R> defaultValue;
    private final Function<T, R> transform;

    CopyOperation(BiFunction<Map<String, Object>, String, Optional<Object>> getFromMap,
                  String sourceKey,
                  String targetKey,
                  Supplier<R> defaultValue,
                  Function<T, R> transform) {
        this.getFromMap = requireNonNull(getFromMap);
        this.sourceKey = requireNonNull(sourceKey);
        this.targetKey = requireNonNull(targetKey);
        this.defaultValue = requireNonNull(defaultValue);
        this.transform = requireNonNull(transform);
    }

    @Override
    public void accept(Map<String, Object> input, Map<String, Object> output) {
        final R value = getFromMap.apply(input, sourceKey)
                .map(MapperImpl::<T>cast)
                .map(transform)
                .orElseGet(defaultValue);

        if (value != null) {
            output.put(targetKey, value);
        }
    }
}
