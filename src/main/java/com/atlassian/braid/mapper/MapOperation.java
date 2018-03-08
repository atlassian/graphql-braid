package com.atlassian.braid.mapper;

import java.util.Map;
import java.util.function.Predicate;

import static java.util.Objects.requireNonNull;

final class MapOperation implements MapperOperation {

    private final String key;
    private final Mapper mapper;
    private final Predicate<MapperInputOutput> predicate;

    MapOperation(String key, Predicate<MapperInputOutput> predicate, Mapper mapper) {
        this.key = requireNonNull(key);
        this.predicate = requireNonNull(predicate);
        this.mapper = requireNonNull(mapper);
    }

    @Override
    public void accept(Map<String, Object> input, Map<String, Object> output) {
        if (predicate.test(MapperInputOutputPair.of(input, output))) {
            output.put(key, mapper.apply(input));
        }
    }
}
