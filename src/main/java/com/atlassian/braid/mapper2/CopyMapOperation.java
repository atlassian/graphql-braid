package com.atlassian.braid.mapper2;

import com.atlassian.braid.collections.BraidObjects;
import com.atlassian.braid.collections.Maps;

import java.util.Map;

import static java.util.Objects.requireNonNull;

final class CopyMapOperation implements MapperOperation {

    private final String sourceKey;
    private final String targetKey;
    private final NewMapper mapper;

    CopyMapOperation(String sourceKey, String targetKey, NewMapper mapper) {
        this.sourceKey = requireNonNull(sourceKey);
        this.targetKey = requireNonNull(targetKey);
        this.mapper = requireNonNull(mapper);
    }

    @Override
    public void accept(Map<String, Object> input, Map<String, Object> output) {
        Maps.get(input, sourceKey)
                .map(BraidObjects::<Map<String, Object>>cast)
                .map(mapper)
                .ifPresent(mappedList -> output.put(targetKey, mappedList));
    }
}
