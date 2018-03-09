package com.atlassian.braid.mapper;

import java.util.Map;
import java.util.Objects;

import static java.util.Collections.unmodifiableMap;

final class MapperInputOutputPair implements MapperInputOutput {

    private final Map<String, Object> input;
    private final Map<String, Object> output;

    private MapperInputOutputPair(Map<String, Object> input, Map<String, Object> output) {
        this.input = unmodifiableMap(Objects.requireNonNull(input));
        this.output = unmodifiableMap(Objects.requireNonNull(output));
    }

    @Override
    public Map<String, Object> getInput() {
        return input;
    }

    @Override
    public Map<String, Object> getOutput() {
        return output;
    }

    static MapperInputOutput of(Map<String, Object> input, Map<String, Object> output) {
        return new MapperInputOutputPair(input, output);
    }
}
