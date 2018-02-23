package com.atlassian.braid.mapper;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;


public interface Mapper extends UnaryOperator<Map<String, Object>> {

    default Mapper copy(String key) {
        return copy(key, key);
    }

    default <T> Mapper copy(String key, Supplier<T> defaultValue) {
        return copy(key, key, defaultValue);
    }

    default <T, R> Mapper copy(String key, Function<T, R> transform) {
        return copy(key, key, () -> null, transform);
    }

    default Mapper copy(String sourceKey, String targetKey) {
        return copy(sourceKey, targetKey, () -> null);
    }

    default <T> Mapper copy(String sourceKey, String targetKey, Supplier<T> defaultValue) {
        return copy(sourceKey, targetKey, defaultValue, Function.identity());
    }

    <T, R> Mapper copy(String sourceKey, String targetKey, Supplier<R> defaultValue, Function<T, R> transform);

    <V> Mapper put(String key, V value);

    default Mapper copyList(String sourceKey, Mapper mapper) {
        return copyList(sourceKey, sourceKey, mapper);
    }

    Mapper copyList(String sourceKey, String targetKey, Mapper mapper);

    Mapper list(String key, Mapper mapper);

    Mapper map(String key, Mapper mapper);

    default Mapper copyMap(String sourceKey, Mapper mapper) {
        return copyMap(sourceKey, sourceKey, mapper);
    }

    Mapper copyMap(String sourceKey, String targetKey, Mapper mapper);
}
