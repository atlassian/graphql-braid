package com.atlassian.braid.mapper;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.atlassian.braid.java.util.BraidObjects.cast;
import static com.atlassian.braid.java.util.BraidPreconditions.checkState;
import static com.atlassian.braid.mapper.MapperMaps.mergeMaps;
import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNull;

final class MapOperation implements MapperOperation {

    private final String key;
    private final Function<Map<String, Object>, Map<String, Object>> mapper;
    private final Predicate<MapperInputOutput> predicate;

    MapOperation(String key, Predicate<MapperInputOutput> predicate, Function<Map<String, Object>, Map<String, Object>> mapper) {
        this.key = requireNonNull(key);
        this.predicate = requireNonNull(predicate);
        this.mapper = requireNonNull(mapper);
    }

    @Override
    public void accept(Map<String, Object> input, Map<String, Object> output) {
        if (predicate.test(MapperInputOutputPair.of(input, output))) {
            output.put(key, mergeMaps(getExistingMapValue(output, key), mapper.apply(input)));
        }
    }

    private static Map<String, Object> getExistingMapValue(Map<String, Object> map, String key) {
        final Object existingValue = map.get(key);
        if (existingValue != null) {
            checkState(existingValue instanceof Map);
            return cast(existingValue);
        } else {
            return emptyMap();
        }
    }
}
