package com.atlassian.braid.mapper2;

import java.util.Map;

import static java.util.Objects.requireNonNull;

final class PutOperation<V> implements MapperOperation {

    private final String key;
    private final V value;

    PutOperation(String key, V value) {
        this.key = requireNonNull(key);
        this.value = requireNonNull(value);
    }

    @Override
    public void accept(Map<String, Object> input, Map<String, Object> output) {
        output.put(key, value);
    }
}
