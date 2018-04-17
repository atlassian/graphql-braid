package com.atlassian.braid.java.util;

import static java.lang.String.format;

public final class BraidPreconditions {

    private BraidPreconditions() {
    }

    public static void checkState(boolean expression) {
        if (!expression) {
            throw new IllegalStateException();
        }
    }

    public static void checkState(boolean expression, String msg, Object... args) {
        if (!expression) {
            throw new IllegalStateException(format(msg, args));
        }
    }
}
