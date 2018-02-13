package com.atlassian.braid.mapper.operation;

import com.atlassian.braid.mapper.FieldOperation;
import com.atlassian.braid.mapper.Mapper;

import java.util.List;
import java.util.Map;

/**
 * Copies a field
 */
public class CopyFieldOperation implements FieldOperation {
    private final String name;
    private final String source;
    public CopyFieldOperation(String name, Map<String, Object> data, List<FieldOperation> ops) {
        this.name = name;
        this.source = (String) data.getOrDefault("source", name);
        if (!ops.isEmpty()) {
            throw new IllegalArgumentException();
        }
    }

    @Override
    public void execute(Mapper mapper) {
        mapper.copy(source, name);
    }
}
