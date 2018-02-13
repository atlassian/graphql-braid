package com.atlassian.braid.mapper.operation;

import com.atlassian.braid.mapper.FieldOperation;
import com.atlassian.braid.mapper.Mapper;

import java.util.List;

/**
 * Creates a new map
 */
public class MapFieldOperation implements FieldOperation {
    private final String name;
    private final List<FieldOperation> ops;
    public MapFieldOperation(String name, List<FieldOperation> ops) {
        this.name = name;
        this.ops = ops;
        if (ops.isEmpty()) {
            throw new IllegalArgumentException();
        }
    }

    @Override
    public void execute(Mapper mapper) {
        Mapper child = mapper.map(name);
        for (FieldOperation op : ops) {
            op.execute(child);
        }
        child.done();
    }
}
