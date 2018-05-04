package com.atlassian.braid.java.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collector;

import static com.atlassian.braid.java.util.BraidCollectors.SingletonCharacteristics.ALLOW_MULTIPLE_OCCURRENCES;
import static java.lang.String.format;
import static java.util.Arrays.asList;

/**
 * Utility class to help working with collectors.
 * <strong>Note</strong> this is an internal class only, and should not be considered part of the Braid API
 */
public final class BraidCollectors {

    private BraidCollectors() {
    }

    /**
     * Collector that expects the stream to be composed of a single element and will return it.
     *
     * @param <T> the type of the returned element
     * @return the single stream element
     * @throws IllegalStateException if the stream has 0 or more than 1 elements
     */
    public static <T> Collector<T, ?, T> singleton() {
        return singleton(new SingletonCharacteristics[0]);
    }

    public static <T> Collector<T, ?, T> singleton(SingletonCharacteristics... characteristics) {
        return singleton("Expected only one element", new Object[0], characteristics);
    }

    public static <T> Collector<T, ?, T> singleton(String msg, Object[] args, SingletonCharacteristics... characteristics) {

        return Collector.<T, Collection<T>, T>of(
                asList(characteristics).contains(ALLOW_MULTIPLE_OCCURRENCES) ? HashSet::new : ArrayList::new,
                Collection::add,
                (left, right) -> {
                    left.addAll(right);
                    return left;
                },
                collection -> {
                    if (collection.size() != 1) {
                        throw new IllegalStateException(format(msg, args));
                    }
                    return collection.iterator().next();
                }
        );
    }


    /**
     * Collects elements into a map, {@code null} values are allowed.
     */
    public static <T, K, V> Collector<T, Map<K, V>, Map<K, V>> nullSafeToMap(Function<T, K> keyMapper,
                                                                             Function<T, V> valueMapper) {
        return Collector.of(HashMap::new, mergeEntry(keyMapper, valueMapper), BraidCollectors::mergeMaps);
    }

    private static <K, V> Map<K, V> mergeMaps(Map<K, V> map1, Map<K, V> map2) {
        final Map<K, V> newMap = new HashMap<>(map1);
        map2.forEach(mergeEntry(newMap));
        return newMap;
    }

    private static <T, K, V> BiConsumer<Map<K, V>, T> mergeEntry(Function<T, K> key, Function<T, V> value) {
        return (map, t) -> mergeEntry(map, key.apply(t), value.apply(t));
    }

    private static <K, V> BiConsumer<K, V> mergeEntry(Map<K, V> map) {
        return (k, v) -> mergeEntry(map, k, v);
    }

    private static <K, V> void mergeEntry(Map<K, V> map, K key, V value) {
        final V existingValue = map.putIfAbsent(key, value);
        if (existingValue != null && !Objects.equals(value, existingValue)) {
            throw new IllegalStateException(
                    format("Error merging {%s:%s} in map, existing value is: %s", key, value, existingValue));
        }
    }

    public enum SingletonCharacteristics {
        /**
         * Allows multiple occurences of the same instance when expecting singleton
         */
        ALLOW_MULTIPLE_OCCURRENCES
    }
}
