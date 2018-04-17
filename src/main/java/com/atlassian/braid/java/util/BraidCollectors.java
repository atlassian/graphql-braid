package com.atlassian.braid.java.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.stream.Collector;

import static com.atlassian.braid.java.util.BraidCollectors.SingletonCharacteristics.ALLOW_MULTIPLE_OCCURRENCES;
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
                        throw new IllegalStateException(String.format(msg, args));
                    }
                    return collection.iterator().next();
                }
        );
    }


    public enum SingletonCharacteristics {
        /**
         * Allows multiple occurences of the same instance when expecting singleton
         */
        ALLOW_MULTIPLE_OCCURRENCES
    }
}
