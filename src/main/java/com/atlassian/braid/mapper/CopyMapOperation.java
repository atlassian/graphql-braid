package com.atlassian.braid.mapper;

import com.atlassian.braid.java.util.BraidObjects;
import com.atlassian.braid.java.util.BraidMaps;

import java.util.Map;

import static java.util.Objects.requireNonNull;

final class CopyMapOperation implements MapperOperation {

    private final String sourceKey;
    private final String targetKey;
    private final Mapper mapper;

    CopyMapOperation(String sourceKey, String targetKey, Mapper mapper) {
        this.sourceKey = requireNonNull(sourceKey);
        this.targetKey = requireNonNull(targetKey);
        this.mapper = requireNonNull(mapper);
    }

    @Override
    public void accept(Map<String, Object> input, Map<String, Object> output) {
        BraidMaps.get(input, sourceKey)
                .map(BraidObjects::<Map<String, Object>>cast)
                .map(mapper)
                .ifPresent(mappedList -> output.put(targetKey, mappedList));
    }
}
