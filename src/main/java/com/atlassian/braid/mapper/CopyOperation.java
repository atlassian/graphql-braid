package com.atlassian.braid.mapper;

import com.atlassian.braid.collections.BraidObjects;
import com.atlassian.braid.collections.Maps;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

final class CopyOperation<T, R> implements MapperOperation {
    private static BiFunction<Map<String, Object>, String, Optional<Object>> getFromMap;

    static {
        try {
            getFromMap = CopyOperation.<SpringExpressions>newInstance("com.atlassian.braid.mapper.SpringExpressions")::get;
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
            LoggerFactory.getLogger(CopyOperation.class).debug("Spring not found, using simple property expressions", e);
            getFromMap = Maps::get;
        }
    }

    private final String sourceKey;
    private final String targetKey;
    private final Supplier<R> defaultValue;
    private final Function<T, R> transform;

    CopyOperation(String sourceKey,
                  String targetKey,
                  Supplier<R> defaultValue,
                  Function<T, R> transform) {
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

    private static <T> T newInstance(String name) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        return BraidObjects.cast(Mapper.class.getClassLoader().loadClass(name).newInstance());
    }
}
