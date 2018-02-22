package com.atlassian.braid.mapper2;

import java.util.Map;
import java.util.function.BiConsumer;

import static com.atlassian.braid.mapper2.MapperOperations.composed;

@FunctionalInterface
interface MapperOperation extends BiConsumer<Map<String, Object>, Map<String, Object>> {

    void accept(Map<String, Object> input, Map<String, Object> output);

    default MapperOperation andThen(MapperOperation after) {
        return composed(this, after);
    }
}
