package com.atlassian.braid.collections;

import java.util.Map;
import java.util.Optional;

public final class Maps {

    private Maps() {
    }

    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> cast(Object map) {
        return (Map<K, V>) map;
    }

    public static <K, V> Optional<V> get(Map<K, V> map, K key) {
        return Optional.ofNullable(map.get(key));
    }
}
