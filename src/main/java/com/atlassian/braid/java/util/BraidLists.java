package com.atlassian.braid.java.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.groupingBy;

/**
 * Utility class to help working with lists
 */
public final class BraidLists {

    private BraidLists() {
    }

    public static <T> List<T> concat(List<? extends T> l, T t) {
        return concat(l, singletonList(t));
    }

    public static <T> List<T> concat(List<? extends T> l1, List<? extends T> l2) {
        ArrayList<T> l = new ArrayList<>(l1);
        l.addAll(l2);
        l.trimToSize();
        return l;
    }

    public static <T> Map<Class<?>, List<T>> groupByType(List<T> list) {
        return list.stream().collect(groupingBy(T::getClass));
    }
}
