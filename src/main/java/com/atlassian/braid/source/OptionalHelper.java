package com.atlassian.braid.source;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Helper classes when dealing with nullable objects and needing to cast the result
 */
class OptionalHelper {

    static <T> Optional<T> castNullable(Object value, Class<T> type) {
        return Optional.ofNullable(value)
                .filter(type::isInstance)
                .map(type::cast);
    }

    static <T> Optional<List<T>> castNullableList(Object value, Class<T> type) {
        //noinspection unchecked
        return Optional.ofNullable(value)
                .filter(List.class::isInstance)
                .map(List.class::cast)
                .map(lst -> (List<T>) lst.stream()
                        .filter(type::isInstance)
                        .map(type::cast)
                        .collect(Collectors.<T>toList())
                );
    }

    static <K, V> Optional<Map<K, V>> castNullableMap(Object value, Class<K> keyType, Class<V> valueType) {
        //noinspection unchecked
        return Optional.ofNullable(value)
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(lst -> ((Set<Map.Entry>)lst.entrySet()).stream()
                        .filter(e -> keyType.isInstance(e.getKey()) && valueType.isInstance(e.getValue()))
                        .collect(Collectors.toMap(e -> keyType.cast(e.getKey()), e -> valueType.cast(e.getValue())))
                );
    }
}
