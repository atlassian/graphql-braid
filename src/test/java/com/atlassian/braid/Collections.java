package com.atlassian.braid;

import java.util.List;
import java.util.Map;

public final class Collections {
    private Collections() {
    }

    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> castMap(Object input) {
        return (Map<K, V>) input;
    }


    @SuppressWarnings("unchecked")
    public static <T> List<T> castList(Object input) {
        return (List<T>) input;
    }

    public static <K, K1, V1> Map<K1, V1> getMapValue(Map<K, Object> map, K key) {
        return castMap(map.get(key));
    }

    public static <K, T> List<T> getListValue(Map<K, Object> map, K key) {
        return castList(map.get(key));
    }
}
