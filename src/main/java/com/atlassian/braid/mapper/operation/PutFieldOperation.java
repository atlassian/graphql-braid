package com.atlassian.braid.mapper.operation;

import com.atlassian.braid.mapper.FieldOperation;
import com.atlassian.braid.mapper.Mapper;

import java.util.List;
import java.util.Map;

/**
 * Puts a value into a field
 */
public class PutFieldOperation implements FieldOperation {
    private final String name;
    private final String value;
    public PutFieldOperation(String name, Map<String, Object> data, List<FieldOperation> ops) {
        this.name = name;
        this.value = (String) data.get("value");
        if (!ops.isEmpty()) {
            throw new IllegalArgumentException();
        }
    }

    @Override
    public void execute(Mapper mapper) {
        mapper.put(name, value);
    }
}
