package com.atlassian.braid.java.util;

import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Utility class to help working with optionals (useful additions to {@link java.util.Optional}.
 * <strong>Note</strong> this is an internal class only, and should not be considered part of the Braid API
 */
public final class BraidOptionals {

    @SafeVarargs
    public static <T> Optional<T> firstNonEmpty(Supplier<Optional<T>>... optionals) {
        return Stream.of(optionals)
                .map(Supplier::get)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }
}
