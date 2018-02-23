package com.atlassian.braid.java.util;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collector;

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
    public static <T> Collector<T, List<T>, T> singleton() {
        return singleton("Expected only one element");
    }

    public static <T> Collector<T, List<T>, T> singleton(String msg, Object... args) {
        return Collector.of(
                ArrayList::new,
                List::add,
                (left, right) -> {
                    left.addAll(right);
                    return left;
                },
                list -> {
                    if (list.size() != 1) {
                        throw new IllegalStateException(String.format(msg, args));
                    }
                    return list.get(0);
                }
        );
    }
}
