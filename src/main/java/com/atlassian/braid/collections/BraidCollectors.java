package com.atlassian.braid.collections;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collector;

public final class BraidCollectors {

    private BraidCollectors() {
    }

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
