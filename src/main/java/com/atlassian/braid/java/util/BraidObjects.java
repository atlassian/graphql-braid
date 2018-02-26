package com.atlassian.braid.java.util;

/**
 * Utility class to help working with objects (useful additions to {@link java.util.Objects}.
 * <strong>Note</strong> this is an internal class only, and should not be considered part of the Braid API
 */
public final class BraidObjects {

    private BraidObjects() {
    }

    /**
     * Allows casting a object to any (generic) type with no warning
     *
     * @param o   the object input
     * @param <T> the type to cast to
     * @return the <strong>same</strong> object but with the expected type
     */
    @SuppressWarnings("unchecked")
    public static <T> T cast(Object o) {
        return (T) o;
    }
}