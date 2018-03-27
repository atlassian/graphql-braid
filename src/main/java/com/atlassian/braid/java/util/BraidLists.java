package com.atlassian.braid.java.util;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.singletonList;

/**
 * Utility class to help working with lists
 */
public final class BraidLists {

    private BraidLists() {
    }

    public static <T> List<T> concat(List<T> l, T t) {
        return concat(l, singletonList(t));
    }

    public static <T> List<T> concat(List<T> l1, List<T> l2) {
        ArrayList<T> l = new ArrayList<>(l1);
        l.addAll(l2);
        l.trimToSize();
        return l;
    }
}
