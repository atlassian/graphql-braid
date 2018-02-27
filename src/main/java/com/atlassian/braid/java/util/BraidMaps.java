package com.atlassian.braid.java.util;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/**
 * Utility class to help working with maps.
 * <strong>Note</strong> this is an internal class only, and should not be considered part of the Braid API
 */
public final class BraidMaps {

    private BraidMaps() {
    }

    public static <K, V> Optional<V> get(Map<K, V> map, K key) {
        return Optional.ofNullable(map.get(key));
    }

    public static <K, V> Map.Entry<K, V> firstEntry(Map<K, V> map) {
        final Iterator<Map.Entry<K, V>> iterator = map.entrySet().iterator();
        return iterator.hasNext() ? iterator.next() : null;
    }
}
