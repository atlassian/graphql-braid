package com.atlassian.braid.mapper.operation;

import com.atlassian.braid.mapper.FieldOperation;
import com.atlassian.braid.mapper.Mapper;

import java.util.List;
import java.util.Map;

/**
 * Copies a map, applying the list of operations to the source map
 */
public class CopyMapFieldOperation implements FieldOperation {
    private final String name;
    private final List<FieldOperation> ops;
    private final String source;
    public CopyMapFieldOperation(String name, Map<String, Object> data, List<FieldOperation> ops) {
        this.name = name;
        this.ops = ops;
        this.source = (String) data.getOrDefault("source", name);
        if (ops.isEmpty()) {
            throw new IllegalArgumentException();
        }
    }

    @Override
    public void execute(Mapper mapper) {
        Mapper child = mapper.copyMap(source, name);
        for (FieldOperation op : ops) {
            op.execute(child);
        }
        child.done();
    }
}
