package com.atlassian.braid.java.util;

public final class BraidPreconditions {

    private BraidPreconditions() {
    }

    public static void checkState(boolean expression) {
        if (!expression) {
            throw new IllegalStateException();
        }
    }
}
