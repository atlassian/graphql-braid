package com.atlassian.braid.mapper2;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;


public interface NewMapper extends UnaryOperator<Map<String, Object>> {

    default NewMapper copy(String key) {
        return copy(key, key);
    }

    default <T> NewMapper copy(String key, Supplier<T> defaultValue) {
        return copy(key, key, defaultValue);
    }

    default <T, R> NewMapper copy(String key, Function<T, R> transform) {
        return copy(key, key, () -> null, transform);
    }

    default NewMapper copy(String sourceKey, String targetKey) {
        return copy(sourceKey, targetKey, () -> null);
    }

    default <T> NewMapper copy(String sourceKey, String targetKey, Supplier<T> defaultValue) {
        return copy(sourceKey, targetKey, defaultValue, Function.identity());
    }

    <T, R> NewMapper copy(String sourceKey, String targetKey, Supplier<R> defaultValue, Function<T, R> transform);

    <V> NewMapper put(String key, V value);

    default NewMapper copyList(String sourceKey, NewMapper mapper) {
        return copyList(sourceKey, sourceKey, mapper);
    }

    NewMapper copyList(String sourceKey, String targetKey, NewMapper mapper);

    NewMapper list(String key, NewMapper mapper);

    NewMapper map(String key, NewMapper mapper);

    default NewMapper copyMap(String sourceKey, NewMapper mapper) {
        return copyMap(sourceKey, sourceKey, mapper);
    }

    NewMapper copyMap(String sourceKey, String targetKey, NewMapper mapper);
}
