package com.atlassian.braid.collections;

public final class BraidObjects {

    private BraidObjects() {
    }

    @SuppressWarnings("unchecked")
    public static <T> T cast(Object o) {
        return (T) o;
    }
}