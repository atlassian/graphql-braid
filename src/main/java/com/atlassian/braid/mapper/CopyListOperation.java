package com.atlassian.braid.mapper;

import com.atlassian.braid.collections.BraidObjects;
import com.atlassian.braid.collections.Maps;

import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

final class CopyListOperation implements MapperOperation {

    private final String sourceKey;
    private final String targetKey;
    private final Mapper mapper;

    CopyListOperation(String sourceKey, String targetKey, Mapper mapper) {
        this.sourceKey = requireNonNull(sourceKey);
        this.targetKey = requireNonNull(targetKey);
        this.mapper = requireNonNull(mapper);
    }

    @Override
    public void accept(Map<String, Object> input, Map<String, Object> output) {
        Maps.get(input, sourceKey)
                .map(BraidObjects::<List<Map<String, Object>>>cast)
                .map(this::mapList)
                .ifPresent(mappedList -> output.put(targetKey, mappedList));
    }

    private List<Map<String, Object>> mapList(List<Map<String, Object>> input) {
        return input.stream()
                .map(mapper)
                .collect(toList());
    }
}
