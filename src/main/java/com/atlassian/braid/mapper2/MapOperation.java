package com.atlassian.braid.mapper2;

import java.util.Map;

import static java.util.Objects.requireNonNull;

final class MapOperation implements MapperOperation {

    private final String key;
    private final NewMapper mapper;

    MapOperation(String key, NewMapper mapper) {
        this.key = requireNonNull(key);
        this.mapper = requireNonNull(mapper);
    }

    @Override
    public void accept(Map<String, Object> input, Map<String, Object> output) {
        output.put(key, mapper.apply(input));
    }
}
